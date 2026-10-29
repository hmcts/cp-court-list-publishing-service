package uk.gov.hmcts.cp.task;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;
import uk.gov.hmcts.cp.models.CourtCentreData;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.services.CaTHService;
import uk.gov.hmcts.cp.services.CourtListDataService;
import uk.gov.hmcts.cp.services.CourtListStatusUpdater;
import uk.gov.hmcts.cp.services.JsonSchemaValidatorService;
import uk.gov.hmcts.cp.services.ReferenceDataService;
import uk.gov.hmcts.cp.services.crown.CrownListTransformationService;
import uk.gov.hmcts.cp.services.crown.CrownListType;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.service.task.ExecutableTask;
import uk.gov.hmcts.cp.taskmanager.service.task.Task;

import java.time.LocalDate;
import java.util.UUID;

import static uk.gov.hmcts.cp.config.AppConstant.ALERT_PATTERN;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo.executionInfo;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus.COMPLETED;

/**
 * Publishes a Crown court list to CaTH as one of the Crown schemas: queries the listing context for the court list,
 * builds the Crown document, validates it against the schema CaTH uses, and sends it. The outcome is recorded on the
 * court list publish status row. Like the other publish tasks it always completes; a failure is recorded on the row
 * and logged with the alert pattern.
 */
@Task(CrownPublishTask.TASK_NAME)
@Component
public class CrownPublishTask implements ExecutableTask {

    public static final String TASK_NAME = "CROWN_PUBLISH_TASK";

    private static final String CROWN_COURT_CODE = "C";

    private static final Logger LOGGER = LoggerFactory.getLogger(CrownPublishTask.class);

    private final CourtListStatusUpdater statusUpdater;
    private final CourtListDataService courtListDataService;
    private final ReferenceDataService referenceDataService;
    private final CrownListTransformationService transformationService;
    private final JsonSchemaValidatorService schemaValidator;
    private final CaTHService cathService;
    private final boolean cathPublishingEnabled;

    public CrownPublishTask(final CourtListStatusUpdater statusUpdater,
                           final CourtListDataService courtListDataService,
                           final ReferenceDataService referenceDataService,
                           final CrownListTransformationService transformationService,
                           final JsonSchemaValidatorService schemaValidator,
                           final CaTHService cathService,
                           @Value("${cath.publishing-enabled:false}") final boolean cathPublishingEnabled) {
        this.statusUpdater = statusUpdater;
        this.courtListDataService = courtListDataService;
        this.referenceDataService = referenceDataService;
        this.transformationService = transformationService;
        this.schemaValidator = schemaValidator;
        this.cathService = cathService;
        this.cathPublishingEnabled = cathPublishingEnabled;
    }

    @Override
    public ExecutionInfo execute(final ExecutionInfo executionInfo) {
        LOGGER.info("Executing {} [job {}]", TASK_NAME, executionInfo);

        final JsonObject jobData = executionInfo.getJobData();
        final UUID courtListId = uuid(jobData, JobDataConstant.COURT_LIST_ID);

        if (!cathPublishingEnabled) {
            LOGGER.debug("CaTH publishing is disabled (CATH_PUBLISHING_ENABLED=false), skipping the Crown publish");
            if (courtListId != null) {
                statusUpdater.markPublishFailed(courtListId, new IllegalStateException(
                        "CaTH publishing is disabled (cath.publishing-enabled=false); the Crown list was not sent"));
            }
            return completed(executionInfo);
        }

        try {
            publish(jobData, courtListId);
            if (courtListId != null) {
                statusUpdater.markPublishSuccessful(courtListId);
            }
        } catch (Exception e) {
            LOGGER.error("Error {} publishing the Crown list to CaTH for court list ID {}", ALERT_PATTERN, courtListId, e);
            if (courtListId != null) {
                statusUpdater.markPublishFailed(courtListId, e);
            }
        }
        return completed(executionInfo);
    }

    private void publish(final JsonObject jobData, final UUID courtListId) throws Exception {
        final CourtListType courtListType = CourtListType.valueOf(required(jobData, JobDataConstant.COURT_LIST_TYPE).toUpperCase());
        final CrownListType crownListType = CrownListType.from(courtListType);
        final String courtCentreId = required(jobData, JobDataConstant.COURT_CENTRE_ID);
        final LocalDate startDate = LocalDate.parse(required(jobData, JobDataConstant.START_DATE));
        final LocalDate endDate = LocalDate.parse(required(jobData, JobDataConstant.END_DATE));
        final String userId = jobData.getString(JobDataConstant.USER_ID, null);

        final String payloadJson = courtListDataService.getCrownListPayload(courtListType, courtCentreId, startDate, endDate, userId);
        final JsonNode payload = ObjectMapperConfig.getObjectMapper().readTree(payloadJson);

        final CourtCentreData courtCentre = referenceDataService.getCourtCenterDataByCourtCentreId(courtCentreId, userId)
                .orElseThrow(() -> new IllegalStateException("No reference data for court centre " + courtCentreId
                        + "; cannot confirm it is a Crown court"));
        if (!CROWN_COURT_CODE.equalsIgnoreCase(courtCentre.getOucodeL1Code())) {
            throw new IllegalStateException("Court centre " + courtCentreId + " is not a Crown court (oucodeL1Code="
                    + courtCentre.getOucodeL1Code() + "); it cannot be published as a Crown list");
        }

        final String document = transformationService.transform(crownListType, payload, courtCentre, startDate, endDate);
        schemaValidator.validate(document, crownListType.schema());

        cathService.sendCrownListToCaTH(document, crownListType, startDate, endDate,
                courtId(courtCentre, payload, courtCentreId), courtListId);
    }

    /** The numeric court id from reference data, else the one in the listing payload; the publish fails if neither has it. */
    private static String courtId(final CourtCentreData courtCentre, final JsonNode payload, final String courtCentreId) {
        final JsonNode site = payload.path("courtLists").path(0).path("crestCourtSite");
        for (final String candidate : new String[] {courtCentre.getCourtIdNumeric(),
                site.path("crestCourtSiteId").asText(null), site.path("crestCourtId").asText(null)}) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        throw new IllegalStateException("No numeric court id for court centre " + courtCentreId
                + " in reference data or the listing payload");
    }

    private static ExecutionInfo completed(final ExecutionInfo executionInfo) {
        return executionInfo().from(executionInfo).withExecutionStatus(COMPLETED).build();
    }

    private static String required(final JsonObject jobData, final String key) {
        final String value = jobData == null ? null : jobData.getString(key, null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Job data is missing " + key);
        }
        return value;
    }

    private static UUID uuid(final JsonObject jobData, final String key) {
        try {
            final String value = jobData == null ? null : jobData.getString(key, null);
            return value == null ? null : UUID.fromString(value);
        } catch (Exception e) {
            LOGGER.warn("Could not read {} from the job data", key, e);
            return null;
        }
    }
}
