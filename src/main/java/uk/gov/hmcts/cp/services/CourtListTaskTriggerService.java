package uk.gov.hmcts.cp.services;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishResponse;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus;
import uk.gov.hmcts.cp.taskmanager.service.ExecutionService;

import java.util.UUID;

import static java.time.ZonedDateTime.now;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo.executionInfo;


@Service
public class CourtListTaskTriggerService {

    private static final Logger LOG = LoggerFactory.getLogger(CourtListTaskTriggerService.class);
    private static final String TASK_NAME = "PUBLISH_AND_PDF_GENERATION_TASK";
    private static final String ERROR_COURT_CENTRE_ID_REQUIRED = "Court centre ID is required";
    private static final String ERROR_COURT_LIST_TYPE_REQUIRED = "Court list type is required";
    public static final String COURT_LIST_ID_IS_REQUIRED = "Court list ID is required";
    public static final String RESPONSE_IS_REQUIRED = "Response is required";

    @Autowired
    ExecutionService executionService;

    /**
     * Triggers the court list tasks asynchronously.
     */
    @Transactional
    public void triggerCourtListTask(final CourtListPublishResponse response) {
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, RESPONSE_IS_REQUIRED);
        }
        if (response.getCourtListId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, COURT_LIST_ID_IS_REQUIRED);
        }
        if (response.getCourtCentreId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ERROR_COURT_CENTRE_ID_REQUIRED);
        }
        if (response.getCourtListType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ERROR_COURT_LIST_TYPE_REQUIRED);
        }

        LOG.atInfo().log("Triggering court list tasks for court list ID: {}, court centre ID: {} and type: {}",
                response.getCourtListId(), response.getCourtCentreId(), response.getCourtListType());

        // Create jobData with courtListId, courtCentreId, and courtListType
        JsonObject jobData = Json.createObjectBuilder()
                .add("courtListId", response.getCourtListId().toString())
                .add("courtCentreId", response.getCourtCentreId().toString())
                .add("courtListType", response.getCourtListType().toString())
                .build();

        ExecutionInfo executionInfo = executionInfo()
                .withJobData(jobData)
                .withAssignedTaskName(TASK_NAME)
                .withAssignedTaskStartTime(now())
                .withExecutionStatus(ExecutionStatus.STARTED)
                .withShouldRetry(false)
                .build();

        try {
            executionService.executeWith(executionInfo);
            LOG.atInfo().log("Court list publishing task triggered successfully");
        } catch (Exception e) {
            LOG.atError().log("Failed to execute task via ExecutionService: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to trigger court list publishing task: " + e.getMessage(), e);
        }
    }
}
