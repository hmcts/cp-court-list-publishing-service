package uk.gov.hmcts.cp.task;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.domain.CourtListPublishStatusEntity;
import uk.gov.hmcts.cp.repositories.CourtListPublishStatusRepository;
import uk.gov.hmcts.cp.services.PdfGenerationService;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.service.task.ExecutableTask;
import uk.gov.hmcts.cp.taskmanager.service.task.Task;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo.executionInfo;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus.COMPLETED;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus.INPROGRESS;

@Task("PDF_GENERATION_TASK")
@Component
@ConditionalOnProperty(name = "azure.storage.enabled", havingValue = "true")
public class PdfGenerationTask implements ExecutableTask {

    private static final Logger logger = LoggerFactory.getLogger(PdfGenerationTask.class);
    
    private static final String COURT_LIST_ID_KEY = "courtListId";
    private static final String PAYLOAD_KEY = "payload";
    private static final String SAS_URL_KEY = "sasUrl";

    private final PdfGenerationService pdfGenerationService;
    private final CourtListPublishStatusRepository repository;

    public PdfGenerationTask(final PdfGenerationService pdfGenerationService,
                             final CourtListPublishStatusRepository repository) {
        this.pdfGenerationService = pdfGenerationService;
        this.repository = repository;
    }

    @Override
    public ExecutionInfo execute(final ExecutionInfo executionInfo) {
        logger.info("Executing PDF_GENERATION_TASK [job {}]", executionInfo);
        
        JsonObject jobData = executionInfo.getJobData();
        UUID courtListId = extractCourtListId(jobData);
        
        try {
            String sasUrl = generateAndUploadPdf(jobData, courtListId);
            logger.info("PDF generation task completed successfully for job: {}", executionInfo);
            
            recordFileNameWithSasUrl(courtListId, sasUrl);
            
            return buildCompletedExecutionInfo(executionInfo, sasUrl);
        } catch (Exception e) {
            logger.error("Error executing PDF generation task for job: {}", executionInfo, e);
            return buildRetryExecutionInfo(executionInfo);
        }
    }

    private ExecutionInfo buildCompletedExecutionInfo(final ExecutionInfo executionInfo, final String sasUrl) {
        JsonObject updatedJobData = updateJobDataWithSasUrl(executionInfo.getJobData(), sasUrl);
        
        return executionInfo()
                .from(executionInfo)
                .withExecutionStatus(COMPLETED)
                .withJobData(updatedJobData)
                .build();
    }

    private ExecutionInfo buildRetryExecutionInfo(final ExecutionInfo executionInfo) {
        return executionInfo()
                .from(executionInfo)
                .withExecutionStatus(INPROGRESS)
                .withShouldRetry(true)
                .build();
    }

    private JsonObject updateJobDataWithSasUrl(final JsonObject jobData, final String sasUrl) {
        JsonObject baseJobData = jobData != null ? jobData : Json.createObjectBuilder().build();
        var updatedJobDataBuilder = Json.createObjectBuilder(baseJobData);
        
        if (sasUrl != null) {
            updatedJobDataBuilder.add(SAS_URL_KEY, sasUrl);
        }
        
        return updatedJobDataBuilder.build();
    }

    private String generateAndUploadPdf(final JsonObject jobData, final UUID courtListId) throws IOException {
        JsonObject payload = jobData.getJsonObject(PAYLOAD_KEY);

        logger.info("Generating and uploading PDF for court list ID: {}", courtListId);
        String sasUrl = pdfGenerationService.generateAndUploadPdf(payload, courtListId,null);
        logger.info("Successfully generated and uploaded PDF for court list ID: {}. SAS URL: {}", courtListId, sasUrl);
        
        return sasUrl;
    }

    private UUID extractCourtListId(final JsonObject jobData) {
        String courtListIdStr = jobData.getString(COURT_LIST_ID_KEY, null);
        if (courtListIdStr == null) {
            logger.warn("Court list ID is missing in job data");
            throw new IllegalArgumentException("Court list ID is required for PDF generation");
        }

        try {
            return UUID.fromString(courtListIdStr);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid UUID format for courtListId: {}", courtListIdStr, e);
            throw new IllegalArgumentException("Invalid court list ID format: " + courtListIdStr, e);
        }
    }

    private void recordFileNameWithSasUrl(final UUID courtListId, final String sasUrl) {
        try {
            CourtListPublishStatusEntity entity = repository.getByCourtListId(courtListId);
            entity.setFileName(sasUrl);
            entity.setLastUpdated(Instant.now());
            repository.save(entity);
            logger.info("Successfully updated fileName with SAS URL for court list ID: {}", courtListId);
        } catch (Exception e) {
            // Log but don't propagate - PDF generation succeeded, save failure is non-critical
            logger.warn("Failed to update fileName with SAS URL for court list ID: {}. PDF generation succeeded.", courtListId, e);
        }
    }

}
