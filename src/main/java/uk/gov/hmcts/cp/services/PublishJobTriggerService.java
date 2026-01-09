package uk.gov.hmcts.cp.services;


import com.taskmanager.domain.ExecutionInfo;
import com.taskmanager.domain.ExecutionStatus;
import com.taskmanager.service.ExecutionService;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishRequest;

import java.time.ZonedDateTime;

import static com.taskmanager.domain.ExecutionInfo.executionInfo;


@Service
@ConditionalOnBean(ExecutionService.class)
public class PublishJobTriggerService {

    private static final Logger LOG = LoggerFactory.getLogger(PublishJobTriggerService.class);
    private static final String TASK_NAME = "COURT_LIST_PUBLISH_TASK";
    private static final String ERROR_COURT_CENTRE_ID_REQUIRED = "Court centre ID is required";
    private static final String ERROR_COURT_LIST_TYPE_REQUIRED = "Court list type is required";

    private final ExecutionService executionService;

    public PublishJobTriggerService(final ExecutionService executionService) {
        this.executionService = executionService;
    }

    /**
     * Triggers the court list publishing task asynchronously.
     */
    public void triggerCourtListPublishingTask(final CourtListPublishRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request is required");
        }
        if (request.getCourtCentreId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ERROR_COURT_CENTRE_ID_REQUIRED);
        }
        if (request.getCourtListType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ERROR_COURT_LIST_TYPE_REQUIRED);
        }

        LOG.atInfo().log("Triggering court list publishing task for court centre ID: {} and type: {}",
                request.getCourtCentreId(), request.getCourtListType());

        JsonObject jobData = Json.createObjectBuilder()
                .add("payload", Json.createObjectBuilder()
                        .add("courtListType", request.getCourtListType().toString())
                        .add("courtCentreId", request.getCourtCentreId().toString())
                        .build())
                .build();

        ExecutionInfo executionInfo = executionInfo()
                .withJobData(jobData)
                .withAssignedTaskName(TASK_NAME)
                .withAssignedTaskStartTime(ZonedDateTime.now())
                .withExecutionStatus(ExecutionStatus.STARTED)
                .withShouldRetry(false)
                .build();

        executionService.executeWith(executionInfo);

        LOG.atInfo().log("Court list publishing task triggered successfully");
    }
}

