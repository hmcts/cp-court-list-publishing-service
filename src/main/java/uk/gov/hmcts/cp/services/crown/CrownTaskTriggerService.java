package uk.gov.hmcts.cp.services.crown;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.task.JobDataConstant;
import uk.gov.hmcts.cp.task.CrownPublishTask;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus;
import uk.gov.hmcts.cp.taskmanager.service.ExecutionService;

import java.time.LocalDate;
import java.util.UUID;

import static java.time.ZonedDateTime.now;
import static uk.gov.hmcts.cp.config.AppConstant.ALERT_PATTERN;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo.executionInfo;

/**
 * Queues the Crown publish (query, transform, validate, CaTH send) as an async job, in the same way
 * {@link uk.gov.hmcts.cp.services.CourtListTaskTriggerService} queues the standard flow.
 */
@Service
public class CrownTaskTriggerService {

    private static final Logger LOG = LoggerFactory.getLogger(CrownTaskTriggerService.class);

    private final ExecutionService executionService;

    public CrownTaskTriggerService(final ExecutionService executionService) {
        this.executionService = executionService;
    }

    /**
     * @param userId value of the CJSCPPUID request header; the task uses it for its calls to the listing context
     */
    public void triggerCrownPublishTask(final UUID courtListId, final UUID courtCentreId, final CourtListType courtListType,
                                       final LocalDate startDate, final LocalDate endDate, final String userId) {
        LOG.atInfo().log("Triggering Crown publish task for court list ID: {}, court centre ID: {}, type: {}",
                courtListId, courtCentreId, courtListType);

        final JsonObject jobData = Json.createObjectBuilder()
                .add(JobDataConstant.COURT_LIST_ID, courtListId.toString())
                .add(JobDataConstant.COURT_CENTRE_ID, courtCentreId.toString())
                .add(JobDataConstant.COURT_LIST_TYPE, courtListType.toString())
                .add(JobDataConstant.START_DATE, startDate.toString())
                .add(JobDataConstant.END_DATE, endDate.toString())
                .add(JobDataConstant.USER_ID, userId)
                .build();

        final ExecutionInfo executionInfo = executionInfo()
                .withJobData(jobData)
                .withAssignedTaskName(CrownPublishTask.TASK_NAME)
                .withAssignedTaskStartTime(now())
                .withExecutionStatus(ExecutionStatus.STARTED)
                .withShouldRetry(false)
                .build();

        try {
            executionService.executeWith(executionInfo);
            LOG.atInfo().log("Crown publish task triggered successfully for court list ID: {}", courtListId);
        } catch (Exception e) {
            LOG.atError().log("Error {} failed to execute Crown publish task via ExecutionService: {}",
                    ALERT_PATTERN, Encode.forJava(e.getMessage()), e);
            throw new IllegalStateException("Failed to trigger Crown publish task: " + e.getMessage(), e);
        }
    }
}
