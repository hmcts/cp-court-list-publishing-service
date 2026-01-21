package uk.gov.hmcts.cp.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.domain.CourtListPublishStatusEntity;
import uk.gov.hmcts.cp.models.transformed.CourtListDocument;
import uk.gov.hmcts.cp.openapi.model.PublishStatus;
import uk.gov.hmcts.cp.repositories.CourtListPublishStatusRepository;
import uk.gov.hmcts.cp.services.CaTHService;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.service.task.ExecutableTask;
import uk.gov.hmcts.cp.taskmanager.service.task.Task;

import java.time.Instant;
import java.util.UUID;

import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo.executionInfo;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus.COMPLETED;


@Task("COURT_LIST_PUBLISH_TASK")
@Component
public class CourtListPublishTask implements ExecutableTask {

    private static final Logger logger = LoggerFactory.getLogger(CourtListPublishTask.class);
    private static final String PAYLOAD_KEY = "payload";
    private static final String COURT_LIST_ID_KEY = "courtListId";
    private final CourtListPublishStatusRepository repository;
    private final CaTHService cathService;

    private final ObjectMapper objectMapper;

    public CourtListPublishTask(CourtListPublishStatusRepository repository,
                                CaTHService cathService,
                                ObjectMapper objectMapper) {
        this.repository = repository;
        this.cathService = cathService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ExecutionInfo execute(ExecutionInfo executionInfo) {
        logger.info("Executing COURT_LIST_PUBLISH_TASK [job {}]", executionInfo);

        try {
            // Query and send court list data to CaTH
            queryAndSendCourtListToCaTH(executionInfo);
        } catch (Exception e) {
            logger.error("Error querying or sending court list to CaTH", e);
        }

        try {
            updateStatusToExportSuccessful(executionInfo);
        } catch (Exception e) {
            logger.error("Error updating court list publish status to EXPORT_SUCCESSFUL", e);
        }
        
        return executionInfo().from(executionInfo)
                .withExecutionStatus(COMPLETED)
                .build();
    }

    private void queryAndSendCourtListToCaTH(ExecutionInfo executionInfo) {
        JsonObject jobData = executionInfo.getJobData();
        if (jobData == null) {
            logger.warn("Job data is null in execution info, cannot send court list to CaTH");
            return;
        }

        try {
            JsonObject payload = jobData.getJsonObject(PAYLOAD_KEY);
            if (payload == null) {
                logger.warn("Payload is missing in job data, cannot send court list to CaTH");
                return;
            }

            CourtListDocument courtListDocument = extractCourtListDocument(payload);
            if (courtListDocument == null) {
                logger.warn("Could not extract court list document from payload");
                return;
            }

            logger.info("Sending transformed court list document to CaTH endpoint");
            cathService.sendCourtListToCaTH(courtListDocument);
            logger.info("Successfully sent court list document to CaTH endpoint");
        } catch (Exception e) {
            logger.error("Error sending court list to CaTH", e);
            throw new RuntimeException("Failed to send court list to CaTH: " + e.getMessage(), e);
        }
    }

    private CourtListDocument extractCourtListDocument(JsonObject payload) {
        try {
            String jsonString = payload.toString();
            return objectMapper.readValue(jsonString, CourtListDocument.class);
        } catch (Exception e) {
            logger.error("Error extracting court list document from payload", e);
            return null;
        }
    }

    private void updateStatusToExportSuccessful(ExecutionInfo executionInfo) {
        JsonObject jobData = executionInfo.getJobData();
        if (jobData == null) {
            logger.warn("Job data is null in execution info");
            return;
        }

        UUID courtListId = extractCourtListId(jobData);
        if (courtListId == null) {
            logger.warn("Court list ID is null or invalid in job data");
            return;
        }

        CourtListPublishStatusEntity existingCourtListPublishEntity = repository.getByCourtListId(courtListId);
        if (existingCourtListPublishEntity == null) {
            logger.warn("No record found with court list ID: {}", courtListId);
            return;
        }

        existingCourtListPublishEntity.setPublishStatus(PublishStatus.PUBLISH_SUCCESSFUL);
        existingCourtListPublishEntity.setLastUpdated(Instant.now());
        repository.save(existingCourtListPublishEntity);
        logger.info("Successfully updated status to PUBLISH_SUCCESSFUL for court list ID: {}", courtListId);
    }

    private UUID extractCourtListId(JsonObject jobData) {
        try {
            String courtListIdStr = jobData.getString(COURT_LIST_ID_KEY, null);
            return courtListIdStr != null ? UUID.fromString(courtListIdStr) : null;
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid UUID format for courtListId: {}", jobData.getString(COURT_LIST_ID_KEY, null), e);
            return null;
        } catch (Exception e) {
            logger.warn("Could not extract courtListId from JsonObject", e);
            return null;
        }
    }
}
