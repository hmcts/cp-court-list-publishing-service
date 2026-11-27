package uk.gov.hmcts.cp.task;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.filters.tracing.TracingFilter;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.services.CourtListStatusUpdater;
import uk.gov.hmcts.cp.services.crown.CrownListCaTHPublisher;
import uk.gov.hmcts.cp.services.crown.CrownListPdfGenerator;
import uk.gov.hmcts.cp.services.crown.CrownListType;
import uk.gov.hmcts.cp.services.crown.CrownPublishRequest;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.service.task.ExecutableTask;
import uk.gov.hmcts.cp.taskmanager.service.task.Task;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static uk.gov.hmcts.cp.config.AppConstant.ALERT_PATTERN;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo.executionInfo;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus.COMPLETED;

/**
 * Publishes one crown court list to CaTH and records the outcome as the row's publish status. Then, for FINAL and
 * FIRM lists, generates the list's PDF and uploads it to blob storage ({@link CrownListPdfGenerator}), recording
 * the outcome as the row's file status, as {@link CourtListPublishAndPDFGenerationTask} does. The two steps are
 * independent: a failed or disabled CaTH publish does not stop the PDF. WARN lists have no PDF template, so their
 * file status is not changed; nor is it when blob storage is switched off ({@code azure.storage.enabled=false}).
 *
 * <p>The task always completes: a failure is recorded as publish status (or file status) FAILED, with an error
 * naming the list and its dates, and logged with {@link uk.gov.hmcts.cp.config.AppConstant#ALERT_PATTERN}.
 * With CaTH publishing switched off, the publish is recorded as FAILED with that reason, so it does not look as
 * if it is still running. The trace context of the request that queued the task ({@code traceId}, {@code spanId})
 * is set in the log context while the task runs; afterwards the thread's own values (if any) are put back.
 */
@Task(CrownListPublishTask.TASK_NAME)
@Component
@Slf4j
public class CrownListPublishTask implements ExecutableTask {

    public static final String TASK_NAME = "CROWN_LIST_PUBLISH_TASK";
    static final String START_DATE = "startDate";
    static final String END_DATE = "endDate";
    static final String COURT_ID_NUMERIC = "courtIdNumeric";
    static final String COURT_TELEPHONE = "courtTelephone";
    static final String PUBLISHING_DISABLED = "CaTH publishing is disabled (CATH_PUBLISHING_ENABLED=false)";
    /** Log context keys carried from the request into the task, as set by {@link TracingFilter}. */
    public static final List<String> TRACE_KEYS = List.of(TracingFilter.TRACE_ID, TracingFilter.SPAN_ID);

    private final CourtListStatusUpdater statusUpdater;
    private final CrownListCaTHPublisher publisher;
    private final Optional<CrownListPdfGenerator> pdfGenerator;
    private final boolean cathPublishingEnabled;

    public CrownListPublishTask(CourtListStatusUpdater statusUpdater, CrownListCaTHPublisher publisher,
                                Optional<CrownListPdfGenerator> pdfGenerator,
                                @Value("${cath.publishing-enabled:false}") boolean cathPublishingEnabled) {
        this.statusUpdater = statusUpdater;
        this.publisher = publisher;
        this.pdfGenerator = pdfGenerator;
        this.cathPublishingEnabled = cathPublishingEnabled;
    }

    /** The task's job data for a request. */
    public static JsonObject jobData(CrownPublishRequest request) {
        JsonObjectBuilder data = Json.createObjectBuilder()
                .add(JobDataConstant.COURT_LIST_ID, request.courtListId().toString())
                .add(JobDataConstant.COURT_CENTRE_ID, request.courtCentreId().toString())
                .add(JobDataConstant.COURT_LIST_TYPE, request.listType().courtListType().name())
                .add(START_DATE, request.startDate().toString())
                .add(END_DATE, request.endDate().toString())
                .add(JobDataConstant.USER_ID, request.userId())
                .add(COURT_ID_NUMERIC, request.courtIdNumeric());
        if (request.courtTelephone() != null) {
            data.add(COURT_TELEPHONE, request.courtTelephone());
        }
        return data.build();
    }

    @Override
    public ExecutionInfo execute(ExecutionInfo executionInfo) {
        JsonObject jobData = executionInfo.getJobData();
        Map<String, String> previous = restoreTraceContext(jobData);
        try {
            run(jobData);
        } finally {
            // task threads are reused: put back whatever the thread had before, removing what it did not have
            previous.forEach((key, value) -> {
                if (value == null) {
                    MDC.remove(key);
                } else {
                    MDC.put(key, value);
                }
            });
        }
        return executionInfo().from(executionInfo).withExecutionStatus(COMPLETED).build();
    }

    private void run(JsonObject jobData) {
        UUID courtListId = null;
        CrownPublishRequest request;
        try {
            if (jobData == null) {
                throw new IllegalArgumentException("Job data is missing");
            }
            courtListId = UUID.fromString(jobData.getString(JobDataConstant.COURT_LIST_ID));
            request = request(courtListId, jobData);
        } catch (Exception e) {
            log.error("Error {} reading crown list publish job, courtListId={}", ALERT_PATTERN, courtListId, e);
            if (courtListId != null) {
                recordFailure(courtListId, e);
            }
            return;
        }
        publishToCaTH(request);
        generatePdf(request);
    }

    private void publishToCaTH(CrownPublishRequest request) {
        UUID courtListId = request.courtListId();
        try {
            if (cathPublishingEnabled) {
                publish(request);
                statusUpdater.markPublishSuccessful(courtListId);
            } else {
                log.warn("{}: {} not published, courtListId={}", PUBLISHING_DISABLED, request.describe(), courtListId);
                statusUpdater.markPublishFailed(courtListId, new IllegalStateException(PUBLISHING_DISABLED));
            }
        } catch (Exception e) {
            log.error("Error {} publishing crown court list to CaTH, courtListId={}", ALERT_PATTERN, courtListId, e);
            recordFailure(courtListId, e);
        }
    }

    /** Generates and uploads the list's PDF (FINAL and FIRM only) and records the outcome as the file status. */
    private void generatePdf(CrownPublishRequest request) {
        if (!CrownListPdfGenerator.hasPdf(request.listType())) {
            return;
        }
        UUID courtListId = request.courtListId();
        if (pdfGenerator.isEmpty()) {
            log.warn("Blob storage is disabled (AZURE_STORAGE_ENABLED=false): no PDF for {}, courtListId={}",
                    request.describe(), courtListId);
            return;
        }
        try {
            statusUpdater.markFileRequested(courtListId);
            UUID fileId = pdfGenerator.get().generateAndUpload(request);
            statusUpdater.markFileSuccessful(courtListId, fileId);
        } catch (Exception e) {
            log.error("Error {} generating crown court list PDF, {}, courtListId={}", ALERT_PATTERN,
                    request.describe(), courtListId, e);
            recordFileFailure(courtListId,
                    new IllegalStateException(request.describe() + " PDF not generated: " + e.getMessage(), e));
        }
    }

    /**
     * Puts the job's trace context into the log context.
     *
     * @return for each key set, the value the thread had before (null when it had none)
     */
    private static Map<String, String> restoreTraceContext(JsonObject jobData) {
        Map<String, String> previous = new HashMap<>();
        if (jobData == null) {
            return previous;
        }
        for (String key : TRACE_KEYS) {
            String value = jobData.getString(key, null);
            if (value != null) {
                previous.put(key, MDC.get(key));
                MDC.put(key, value);
            }
        }
        return previous;
    }

    private void publish(CrownPublishRequest request) {
        try {
            publisher.publish(request);
        } catch (RuntimeException e) {
            throw new IllegalStateException(request.describe() + " not published to CaTH: " + e.getMessage(), e);
        }
    }

    private static CrownPublishRequest request(UUID courtListId, JsonObject jobData) {
        CourtListType courtListType = CourtListType.valueOf(jobData.getString(JobDataConstant.COURT_LIST_TYPE));
        return new CrownPublishRequest(
                courtListId,
                UUID.fromString(jobData.getString(JobDataConstant.COURT_CENTRE_ID)),
                CrownListType.from(courtListType)
                        .orElseThrow(() -> new IllegalArgumentException("Not a crown list type: " + courtListType)),
                LocalDate.parse(jobData.getString(START_DATE)),
                LocalDate.parse(jobData.getString(END_DATE)),
                jobData.getString(JobDataConstant.USER_ID),
                jobData.getString(COURT_ID_NUMERIC),
                jobData.getString(COURT_TELEPHONE, null));
    }

    private void recordFileFailure(UUID courtListId, Exception cause) {
        try {
            statusUpdater.markFileFailed(courtListId, cause);
        } catch (Exception e) {
            log.error("Error {} recording crown list PDF failure, courtListId={}", ALERT_PATTERN, courtListId, e);
        }
    }

    private void recordFailure(UUID courtListId, Exception cause) {
        try {
            statusUpdater.markPublishFailed(courtListId, cause);
        } catch (Exception e) {
            log.error("Error {} recording crown list publish failure, courtListId={}", ALERT_PATTERN, courtListId, e);
        }
    }
}
