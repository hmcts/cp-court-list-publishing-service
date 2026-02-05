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
    private static final String ERROR_COURT_LIST_PUBLISH_DATE_REQUIRED = "Court list Publish Date is required";
    public static final String COURT_LIST_ID_IS_REQUIRED = "Court list ID is required";
    public static final String RESPONSE_IS_REQUIRED = "Response is required";
    private static final String ERROR_USER_ID_REQUIRED = "CJSCPPUID (user ID) is required";

    @Autowired
    ExecutionService executionService;

    /**
     * Triggers the court list tasks asynchronously.
     * @param makeExternalCalls when true, the task will call CaTH and generate/upload PDF; when false, external calls are skipped (temporary param, to be removed by 2026-02-07).
     * @param userId value from CJSCPPUID request header; stored in job data for query and PDF calls.
     */
    @Transactional
    public void triggerCourtListTask(final CourtListPublishResponse response, final boolean makeExternalCalls, final String userId) {
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
        if (response.getPublishDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ERROR_COURT_LIST_PUBLISH_DATE_REQUIRED);
        }
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ERROR_USER_ID_REQUIRED);
        }

        LOG.atInfo().log("Triggering court list tasks for court list ID: {}, court centre ID: {} and type: {}",
                response.getCourtListId(), response.getCourtCentreId(), response.getCourtListType());

        // Create jobData with courtListId, courtCentreId, courtListType, makeExternalCalls, and userId (CJSCPPUID)
        JsonObject jobData = Json.createObjectBuilder()
                .add("courtListId", response.getCourtListId().toString())
                .add("courtCentreId", response.getCourtCentreId().toString())
                .add("courtListType", response.getCourtListType().toString())
                .add("publishDate", response.getPublishDate().toString())
                .add("makeExternalCalls", makeExternalCalls)
                .add("userId", userId)
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
