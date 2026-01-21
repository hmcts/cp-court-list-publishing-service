package uk.gov.hmcts.cp.task;

import jakarta.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.Optional;
import uk.gov.hmcts.cp.domain.CourtListStatusEntity;
import uk.gov.hmcts.cp.openapi.model.PublishStatus;
import uk.gov.hmcts.cp.repositories.CourtListStatusRepository;
import uk.gov.hmcts.cp.services.CaTHService;
import uk.gov.hmcts.cp.services.CourtListPdfHelper;
import uk.gov.hmcts.cp.services.CourtListQueryService;
import uk.gov.hmcts.cp.services.ListingQueryService;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.service.task.ExecutableTask;
import uk.gov.hmcts.cp.taskmanager.service.task.Task;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo.executionInfo;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus.COMPLETED;


@Task("PUBLISH_AND_PDF_GENERATION_TASK")
@Component
public class CourtListPublishAndPDFGenerationTask implements ExecutableTask {

    private static final Logger logger = LoggerFactory.getLogger(CourtListPublishAndPDFGenerationTask.class);
    private static final String COURT_LIST_ID = "courtListId";
    private static final String COURT_CENTRE_ID = "courtCentreId";
    private static final String COURT_LIST_TYPE = "courtListType";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String GENISIS_USER_ID = "7aee5dea-b0de-4604-b49b-86c7788cfc4b";

    private final CourtListStatusRepository repository;
    private final CourtListQueryService courtListQueryService;
    private final CaTHService cathService;
    private final ListingQueryService listingQueryService;
    private final Optional<CourtListPdfHelper> pdfHelper;

    public CourtListPublishAndPDFGenerationTask(CourtListStatusRepository repository,
                                                CourtListQueryService courtListQueryService,
                                                CaTHService cathService,
                                                ListingQueryService listingQueryService,
                                                @Autowired(required = false) CourtListPdfHelper pdfHelper) {
        this.repository = repository;
        this.courtListQueryService = courtListQueryService;
        this.cathService = cathService;
        this.listingQueryService = listingQueryService;
        this.pdfHelper = Optional.ofNullable(pdfHelper);
    }

    @Override
    public ExecutionInfo execute(ExecutionInfo executionInfo) {
        logger.info("Executing COURT_LIST_PUBLISH_TASK [job {}]", executionInfo);

        // Extract courtListId once for use in status update
        UUID courtListId = null;
        if (executionInfo.getJobData() != null) {
            courtListId = extractCourtListId(executionInfo.getJobData());
        }

        try {
            // Query and send court list data to CaTH
            queryAndSendCourtListToCaTH(executionInfo);
        } catch (Exception e) {
            logger.error("Error querying or sending court list to CaTH", e);
        }

        try {
            // Update status to PUBLISH_SUCCESSFUL after CaTH publishing attempt
            if (courtListId != null) {
                updateStatusToPublishSuccessful(courtListId);
            }
        } catch (Exception e) {
            logger.error("Error updating court list publish status to PUBLISH_SUCCESSFUL", e);
        }

        try {
            // Generate and upload PDF if PDF helper is available
            String sasUrl = generateAndUploadPdf(executionInfo);
            if (sasUrl != null && courtListId != null) {
                // Update fileName and lastUpdated after successful PDF generation
                updateFileNameAndLastUpdated(courtListId, sasUrl);
            }
        } catch (Exception e) {
            logger.error("Error generating and uploading PDF", e);
        }
        
        return executionInfo().from(executionInfo)
                .withExecutionStatus(COMPLETED)
                .build();
    }

    private void queryAndSendCourtListToCaTH(ExecutionInfo executionInfo) {
        JsonObject jobData = executionInfo.getJobData();
        if (jobData == null) {
            logger.warn("Job data is null in execution info, cannot query court list");
            return;
        }

        // Extract parameters from jobData
        String listId = extractListId(jobData);
        String courtCentreId = extractCourtCentreId(jobData);

        if (listId == null || courtCentreId == null) {
            logger.warn("Missing required parameters: listId={}, courtCentreId={}", listId, courtCentreId);
            return;
        }

        // Get today's date for startDate and endDate
        String todayDate = LocalDate.now().format(DATE_FORMATTER);
        logger.info("Querying court list with listId={}, courtCentreId={}, startDate={}, endDate={}",
                listId, courtCentreId, todayDate, todayDate);

        try {
            // Query court list using CourtListQueryService
            var courtListDocument = courtListQueryService.queryCourtList(
                    listId,
                    courtCentreId,
                    todayDate,
                    todayDate,
                    GENISIS_USER_ID
            );

            // Send transformed data to CaTH endpoint
            logger.info("Sending transformed court list document to CaTH endpoint");
            cathService.sendCourtListToCaTH(courtListDocument);
            logger.info("Successfully sent court list document to CaTH endpoint");
        } catch (Exception e) {
            logger.error("Error querying or sending court list to CaTH", e);
            throw new RuntimeException("Failed to query and send court list to CaTH: " + e.getMessage(), e);
        }
    }

    private String generateAndUploadPdf(ExecutionInfo executionInfo) {
        if (pdfHelper.isEmpty()) {
            logger.debug("PDF helper not available, skipping PDF generation");
            return null;
        }

        JsonObject jobData = executionInfo.getJobData();
        if (jobData == null) {
            logger.warn("Job data is null in execution info, cannot generate PDF");
            return null;
        }

        // Extract parameters from jobData
        UUID courtListId = extractCourtListId(jobData);
        String listId = extractListId(jobData);
        String courtCentreId = extractCourtCentreId(jobData);

        if (courtListId == null || listId == null || courtCentreId == null) {
            logger.warn("Missing required parameters for PDF generation: courtListId={}, listId={}, courtCentreId={}",
                    courtListId, listId, courtCentreId);
            return null;
        }

        // Get today's date for startDate and endDate
        String todayDate = LocalDate.now().format(DATE_FORMATTER);
        logger.info("Generating PDF for court list ID: {}, listId: {}, courtCentreId: {}",
                courtListId, listId, courtCentreId);

        try {
            // Fetch payload for PDF generation
            var payload = listingQueryService.getCourtListPayload(
                    listId,
                    courtCentreId,
                    todayDate,
                    todayDate,
                    GENISIS_USER_ID
            );

            if (payload == null) {
                logger.warn("Payload is null, cannot generate PDF for court list ID: {}", courtListId);
                return null;
            }

            // Generate and upload PDF using helper and return the SAS URL
            String sasUrl = pdfHelper.get().generateAndUploadPdf(payload, courtListId);
            logger.info("Successfully generated and uploaded PDF for court list ID: {}", courtListId);
            return sasUrl;
        } catch (Exception e) {
            logger.error("Error generating and uploading PDF for court list ID: {}", courtListId, e);
            // Don't throw - PDF generation failure should not fail the entire task
            return null;
        }
    }

    private String extractListId(JsonObject jobData) {
        try {
            return jobData.getString(COURT_LIST_TYPE, null);
        } catch (Exception e) {
            logger.warn("Could not extract listId (courtListType) from JsonObject", e);
            return null;
        }
    }

    private String extractCourtCentreId(JsonObject jobData) {
        try {
            return jobData.getString(COURT_CENTRE_ID, null);
        } catch (Exception e) {
            logger.warn("Could not extract courtCentreId from JsonObject", e);
            return null;
        }
    }

    private void updateStatusToPublishSuccessful(UUID courtListId) {
        CourtListStatusEntity existingCourtListPublishEntity = repository.getByCourtListId(courtListId);
        if (existingCourtListPublishEntity == null) {
            logger.warn("No record found with court list ID: {}", courtListId);
            return;
        }

        existingCourtListPublishEntity.setPublishStatus(PublishStatus.PUBLISH_SUCCESSFUL);
        existingCourtListPublishEntity.setLastUpdated(Instant.now());
        repository.save(existingCourtListPublishEntity);
        logger.info("Successfully updated status to PUBLISH_SUCCESSFUL for court list ID: {}", courtListId);
    }

    private void updateFileNameAndLastUpdated(UUID courtListId, String fileName) {
        CourtListStatusEntity existingCourtListPublishEntity = repository.getByCourtListId(courtListId);
        if (existingCourtListPublishEntity == null) {
            logger.warn("No record found with court list ID: {}", courtListId);
            return;
        }

        existingCourtListPublishEntity.setFileName(fileName);
        existingCourtListPublishEntity.setLastUpdated(Instant.now());
        repository.save(existingCourtListPublishEntity);
        logger.info("Successfully updated fileName and lastUpdated for court list ID: {}", courtListId);
    }

    private UUID extractCourtListId(JsonObject jobData) {
        try {
            String courtListIdStr = jobData.getString(COURT_LIST_ID, null);
            return courtListIdStr != null ? UUID.fromString(courtListIdStr) : null;
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid UUID format for courtListId: {}", jobData.getString(COURT_LIST_ID, null), e);
            return null;
        } catch (Exception e) {
            logger.warn("Could not extract courtListId from JsonObject", e);
            return null;
        }
    }
}
