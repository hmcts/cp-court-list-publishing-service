package uk.gov.hmcts.cp.services;

import jakarta.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishRequest;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus;
import uk.gov.hmcts.cp.taskmanager.domain.converter.JsonObjectConverter;
import uk.gov.hmcts.cp.taskmanager.service.ExecutionService;

import static java.time.ZonedDateTime.now;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo.executionInfo;


@Service
public class PublishJobTriggerService {

    private static final Logger LOG = LoggerFactory.getLogger(PublishJobTriggerService.class);
    private static final String TASK_NAME = "COURT_LIST_PUBLISH_TASK";
    private static final String ERROR_COURT_CENTRE_ID_REQUIRED = "Court centre ID is required";
    private static final String ERROR_COURT_LIST_TYPE_REQUIRED = "Court list type is required";

    @Autowired
    ExecutionService executionService;

    @Autowired
    JsonObjectConverter objectConverter;


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

        JsonObject jobData = objectConverter.convertFromObject(new CourtListPublishRequest(request.getCourtCentreId(), request.getCourtListType()));

        ExecutionInfo executionInfo = executionInfo()
                .withJobData(jobData)
                .withAssignedTaskName(TASK_NAME)
                .withAssignedTaskStartTime(now())
                .withExecutionStatus(ExecutionStatus.STARTED)
                .withShouldRetry(false)
                .build();

        executionService.executeWith(executionInfo);

        LOG.atInfo().log("Court list publishing task triggered successfully");
    }
}
