package uk.gov.hmcts.cp.task;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.services.CourtListStatusUpdater;
import uk.gov.hmcts.cp.services.crown.CrownListCaTHPublisher;
import uk.gov.hmcts.cp.services.crown.CrownListType;
import uk.gov.hmcts.cp.services.crown.CrownPublishRequest;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.service.task.ExecutableTask;
import uk.gov.hmcts.cp.taskmanager.service.task.Task;

import java.time.LocalDate;
import java.util.UUID;

import static uk.gov.hmcts.cp.config.AppConstant.ALERT_PATTERN;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo.executionInfo;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus.COMPLETED;

/**
 * Publishes one crown court list to CaTH and records the outcome as the row's publish status. It produces no file,
 * so the file status is not changed. The task always completes: a failure is recorded as publish status FAILED,
 * with an error naming the list and its dates, and logged with {@link uk.gov.hmcts.cp.config.AppConstant#ALERT_PATTERN}.
 * With CaTH publishing switched off, the publish is recorded as FAILED with that reason, so it does not look as
 * if it is still running.
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

    private final CourtListStatusUpdater statusUpdater;
    private final CrownListCaTHPublisher publisher;
    private final boolean cathPublishingEnabled;

    public CrownListPublishTask(CourtListStatusUpdater statusUpdater, CrownListCaTHPublisher publisher,
                                @Value("${cath.publishing-enabled:false}") boolean cathPublishingEnabled) {
        this.statusUpdater = statusUpdater;
        this.publisher = publisher;
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
        UUID courtListId = null;
        try {
            if (jobData == null) {
                throw new IllegalArgumentException("Job data is missing");
            }
            courtListId = UUID.fromString(jobData.getString(JobDataConstant.COURT_LIST_ID));
            CrownPublishRequest request = request(courtListId, jobData);
            if (cathPublishingEnabled) {
                publish(request);
                statusUpdater.markPublishSuccessful(courtListId);
            } else {
                log.warn("{}: {} not published, courtListId={}", PUBLISHING_DISABLED, request.describe(), courtListId);
                statusUpdater.markPublishFailed(courtListId, new IllegalStateException(PUBLISHING_DISABLED));
            }
        } catch (Exception e) {
            log.error("Error {} publishing crown court list to CaTH, courtListId={}", ALERT_PATTERN, courtListId, e);
            if (courtListId != null) {
                recordFailure(courtListId, e);
            }
        }
        return executionInfo().from(executionInfo).withExecutionStatus(COMPLETED).build();
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

    private void recordFailure(UUID courtListId, Exception cause) {
        try {
            statusUpdater.markPublishFailed(courtListId, cause);
        } catch (Exception e) {
            log.error("Error {} recording crown list publish failure, courtListId={}", ALERT_PATTERN, courtListId, e);
        }
    }
}
