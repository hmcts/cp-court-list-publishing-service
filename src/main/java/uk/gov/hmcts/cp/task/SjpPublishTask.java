package uk.gov.hmcts.cp.task;

import jakarta.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;
import uk.gov.hmcts.cp.domain.DtsMeta;
import uk.gov.hmcts.cp.domain.sjp.SjpListPayload;
import uk.gov.hmcts.cp.openapi.model.SjpListType;
import uk.gov.hmcts.cp.services.AzureBlobService;
import uk.gov.hmcts.cp.services.CaTHService;
import uk.gov.hmcts.cp.services.CourtListPublisher;
import uk.gov.hmcts.cp.services.CourtListStatusUpdater;
import uk.gov.hmcts.cp.services.JsonSchemaValidatorService;
import uk.gov.hmcts.cp.services.PublicationSchema;
import uk.gov.hmcts.cp.services.sanitization.DocumentSanitizer;
import uk.gov.hmcts.cp.services.sjp.SjpToCathPayloadTransformer;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.service.task.ExecutableTask;
import uk.gov.hmcts.cp.taskmanager.service.task.Task;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static uk.gov.hmcts.cp.config.AppConstant.ALERT_PATTERN;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo.executionInfo;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus.COMPLETED;

/**
 * Async worker for SJP publishing, queued by {@link uk.gov.hmcts.cp.services.sjp.SjpTaskTriggerService}.
 * Mirrors the standard flow's CaTH-send step (transform, sanitize, upload to blob via the same
 * {@link CaTHService#buildBlobName} convention, then publish), with no content dedup — a repeat
 * trigger always re-sends and overwrites the row.
 *
 * <p>Tracked in the shared {@code court_list_publish_status} table; {@code courtCentreId} and
 * file-related columns are always null for SJP rows (national, no PDF).
 */
@Task("SJP_PUBLISH_TASK")
@Component
public class SjpPublishTask implements ExecutableTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(SjpPublishTask.class);

    private static final String SENSITIVITY_PUBLIC = "PUBLIC";
    private static final String SENSITIVITY_CLASSIFIED = "CLASSIFIED";
    private static final String DOCUMENT_NAME_PUBLIC = "SJP Public list";
    private static final String DOCUMENT_NAME_PRESS = "SJP Press list";
    private static final String PROVENANCE = "COMMON_PLATFORM";
    private static final String TYPE_LIST = "LIST";
    private static final String LANGUAGE_WELSH = "WELSH";
    private static final String LANGUAGE_ENGLISH = "ENGLISH";

    /** Press variants (full and delta) carry CLASSIFIED sensitivity and the press schema. */
    private static final java.util.Set<SjpListType> PRESS_LIST_TYPES = java.util.Set.of(
            SjpListType.SJP_PRESS_LIST, SjpListType.SJP_DELTA_PRESS_LIST);

    /** Delta variants (press and public) carry only the changes since the last send. */
    private static final java.util.Set<SjpListType> DELTA_LIST_TYPES = java.util.Set.of(
            SjpListType.SJP_DELTA_PRESS_LIST, SjpListType.SJP_DELTA_PUBLIC_LIST);

    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER = ObjectMapperConfig.getObjectMapper();

    private final CourtListStatusUpdater statusUpdater;
    private final SjpToCathPayloadTransformer transformer;
    private final CourtListPublisher courtListPublisher;
    private final DocumentSanitizer documentSanitizer;
    private final JsonSchemaValidatorService jsonSchemaValidatorService;
    private final Optional<AzureBlobService> azureBlobService;

    public SjpPublishTask(CourtListStatusUpdater statusUpdater,
                           SjpToCathPayloadTransformer transformer,
                           CourtListPublisher courtListPublisher,
                           DocumentSanitizer documentSanitizer,
                           JsonSchemaValidatorService jsonSchemaValidatorService,
                           Optional<AzureBlobService> azureBlobService) {
        this.statusUpdater = statusUpdater;
        this.transformer = transformer;
        this.courtListPublisher = courtListPublisher;
        this.documentSanitizer = documentSanitizer;
        this.jsonSchemaValidatorService = jsonSchemaValidatorService;
        this.azureBlobService = azureBlobService;
    }

    /**
     * What is being published, resolved once from job data so that every log line — including the
     * catch-all failure log — can name the exact variant (press/public, delta/full, language).
     */
    private record SjpPublishContext(UUID courtListId,
                                     SjpListType listType,
                                     String language,
                                     String requestType,
                                     SjpListPayload payload) {

        boolean isPressList() {
            return PRESS_LIST_TYPES.contains(listType);
        }

        boolean isDeltaList() {
            return DELTA_LIST_TYPES.contains(listType);
        }

        /** Human-readable descriptor of the failing/succeeding publish, for log correlation. */
        String describe() {
            return String.format("listType=%s, audience=%s, scope=%s, language=%s, requestType=%s, courtId=%s",
                    listType.getValue(),
                    isPressList() ? "PRESS" : "PUBLIC",
                    isDeltaList() ? "DELTA" : "FULL",
                    language,
                    requestType,
                    payload.getCourtIdNumeric());
        }
    }

    @Override
    public ExecutionInfo execute(ExecutionInfo executionInfo) {
        LOGGER.info("Executing SJP_PUBLISH_TASK [job {}]", executionInfo);

        JsonObject jobData = executionInfo.getJobData();
        if (jobData == null) {
            LOGGER.warn("SJP_PUBLISH_TASK executed with no job data");
            return completed(executionInfo);
        }

        UUID courtListId = extractCourtListId(jobData);
        SjpPublishContext context = null;

        try {
            context = parseContext(courtListId, jobData);
            if (context != null) {
                publish(context);
            }
        } catch (Exception e) {
            LOGGER.error("Error {} publishing SJP court list for courtListId: {}, {}",
                    ALERT_PATTERN, courtListId, describe(context, jobData), e);
            if (courtListId != null) {
                statusUpdater.markPublishFailed(courtListId, e);
            }
        }

        return completed(executionInfo);
    }

    private static ExecutionInfo completed(ExecutionInfo executionInfo) {
        return executionInfo().from(executionInfo).withExecutionStatus(COMPLETED).build();
    }

    /**
     * Resolves job data into a {@link SjpPublishContext}, or {@code null} (having logged why) when
     * the job cannot be published at all.
     */
    private SjpPublishContext parseContext(UUID courtListId, JsonObject jobData) throws Exception {
        String listTypeValue = jobData.getString(JobDataConstant.SJP_LIST_TYPE, null);
        String payloadJson = jobData.getString(JobDataConstant.SJP_PAYLOAD, null);
        String language = jobData.containsKey(JobDataConstant.SJP_LANGUAGE)
                ? jobData.getString(JobDataConstant.SJP_LANGUAGE) : null;
        String requestType = jobData.containsKey(JobDataConstant.SJP_REQUEST_TYPE)
                ? jobData.getString(JobDataConstant.SJP_REQUEST_TYPE) : null;

        if (courtListId == null || listTypeValue == null || payloadJson == null) {
            LOGGER.warn("Missing required job data for SJP publish task, courtListId={}, listType={}", courtListId, listTypeValue);
            return null;
        }

        SjpListType listType;
        try {
            listType = SjpListType.fromValue(listTypeValue);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Unknown SJP list type in job data for courtListId: {}, listType: {}", courtListId, listTypeValue);
            return null;
        }

        SjpListPayload payload = OBJECT_MAPPER.readValue(payloadJson, SjpListPayload.class);

        String payloadLanguage = Boolean.TRUE.equals(payload.getIsWelsh()) ? LANGUAGE_WELSH : LANGUAGE_ENGLISH;
        String resolvedLanguage = (language != null && !language.isBlank()) ? language : payloadLanguage;

        return new SjpPublishContext(courtListId, listType, resolvedLanguage, requestType, payload);
    }

    /**
     * Best-effort descriptor for the failure log: the fully resolved context when we got that far,
     * otherwise whatever raw job data we have (the payload may have failed to parse).
     */
    private static String describe(SjpPublishContext context, JsonObject jobData) {
        if (context != null) {
            return context.describe();
        }
        return String.format("listType=%s, language=%s, requestType=%s (context unresolved)",
                jobData.getString(JobDataConstant.SJP_LIST_TYPE, null),
                jobData.getString(JobDataConstant.SJP_LANGUAGE, null),
                jobData.getString(JobDataConstant.SJP_REQUEST_TYPE, null));
    }

    private void publish(SjpPublishContext context) throws Exception {
        UUID courtListId = context.courtListId();
        // Every attempt cycles the row REQUESTED -> SUCCESSFUL/FAILED, so a previous attempt's
        // outcome (and its error message) never outlives it — including when this job is a
        // re-run rather than a fresh accept.
        statusUpdater.markPublishRequested(courtListId);

        boolean isPressList = context.isPressList();
        String documentName = isPressList ? DOCUMENT_NAME_PRESS : DOCUMENT_NAME_PUBLIC;
        // Forwarded to CaTH verbatim: SjpListType mirrors CaTH's ListType one-to-one, so
        // collapsing delta variants here would make CaTH render delta content with the
        // full-list template.
        String cathListType = context.listType().getValue();
        String sensitivity = isPressList ? SENSITIVITY_CLASSIFIED : SENSITIVITY_PUBLIC;

        String transformedPayload = documentSanitizer.sanitize(transformer.transform(context.payload(), documentName));

        PublicationSchema schema = isPressList ? PublicationSchema.SJP_PRESS : PublicationSchema.SJP_PUBLIC;
        jsonSchemaValidatorService.validate(transformedPayload, schema);

        uploadPayloadToBlob(transformedPayload, courtListId);

        DtsMeta meta = buildDtsMeta(cathListType, sensitivity, context.language(), context.requestType(),
                context.payload().getCourtIdNumeric());
        LOGGER.info("Sending SJP court list to CaTH, courtListId={}, {}, sensitivity={}",
                courtListId, context.describe(), sensitivity);
        int status = courtListPublisher.publish(transformedPayload, meta);
        LOGGER.info("SJP court list published to CaTH, courtListId={}, {}, status={}",
                courtListId, context.describe(), status);

        if (status >= 200 && status < 300) {
            statusUpdater.markPublishSuccessful(courtListId);
        } else {
            RuntimeException cathFailure = new RuntimeException(
                    "CaTH returned status " + status + " for " + context.describe());
            LOGGER.error("Error {} CaTH publish failed for courtListId: {}, {}, status: {}",
                    ALERT_PATTERN, courtListId, context.describe(), status, cathFailure);
            statusUpdater.markPublishFailed(courtListId, cathFailure);
        }
    }

    private void uploadPayloadToBlob(String payload, UUID courtListId) {
        azureBlobService.ifPresentOrElse(
                blobService -> {
                    try {
                        blobService.uploadJson(payload, CaTHService.buildBlobName(courtListId));
                    } catch (Exception e) {
                        LOGGER.error("Error {} uploading SJP payload to blob storage, continuing with publish", ALERT_PATTERN, e);
                    }
                },
                () -> LOGGER.debug("Azure Blob Service not available, skipping SJP payload upload")
        );
    }

    private static DtsMeta buildDtsMeta(String listType, String sensitivity, String language,
                                        String requestType, String courtIdNumeric) {
        final String courtIdForMeta = courtIdNumeric != null && !courtIdNumeric.isBlank()
                ? courtIdNumeric
                : "0";
        Instant now = Instant.now();
        String contentDate = now.toString();
        String displayTo = now.plus(24, ChronoUnit.HOURS).toString();
        return DtsMeta.builder()
                .provenance(PROVENANCE)
                .type(TYPE_LIST)
                .listType(listType)
                .courtId(courtIdForMeta)
                .contentDate(contentDate)
                .language(language)
                .sensitivity(sensitivity)
                .displayFrom(contentDate)
                .displayTo(displayTo)
                .requestType(requestType)
                .build();
    }

    private UUID extractCourtListId(JsonObject jobData) {
        try {
            String value = jobData.getString(JobDataConstant.SJP_LIST_ID, null);
            return value != null ? UUID.fromString(value) : null;
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid UUID format for courtListId: {}", jobData.getString(JobDataConstant.SJP_LIST_ID, null), e);
            return null;
        } catch (Exception e) {
            LOGGER.warn("Could not extract courtListId from JsonObject", e);
            return null;
        }
    }
}
