package uk.gov.hmcts.cp.task;

import jakarta.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.domain.CourtListStatusEntity;
import uk.gov.hmcts.cp.models.CourtListPayload;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.openapi.model.Status;
import uk.gov.hmcts.cp.repositories.CourtListStatusRepository;
import uk.gov.hmcts.cp.services.CaTHService;
import uk.gov.hmcts.cp.services.CourtListPdfHelper;
import uk.gov.hmcts.cp.services.CourtListQueryService;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.service.task.ExecutableTask;
import uk.gov.hmcts.cp.taskmanager.service.task.Task;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo.executionInfo;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus.COMPLETED;


@Task("PUBLISH_AND_PDF_GENERATION_TASK")
@Component
public class CourtListPublishAndPDFGenerationTask implements ExecutableTask {

    private static final Logger logger = LoggerFactory.getLogger(CourtListPublishAndPDFGenerationTask.class);

    private final CourtListStatusRepository repository;
    private final CourtListQueryService courtListQueryService;
    private final CaTHService cathService;
    private final CourtListPdfHelper pdfHelper;
    private enum ErrorContext { PUBLISH, FILE }

    public CourtListPublishAndPDFGenerationTask(CourtListStatusRepository repository,
                                                CourtListQueryService courtListQueryService,
                                                CaTHService cathService,
                                                CourtListPdfHelper pdfHelper) {
        this.repository = repository;
        this.courtListQueryService = courtListQueryService;
        this.cathService = cathService;
        this.pdfHelper = pdfHelper;
    }

    @Override
    public ExecutionInfo execute(ExecutionInfo executionInfo) {
        logger.info("Executing COURT_LIST_PUBLISH_TASK [job {}]", executionInfo);

        JsonObject jobData = executionInfo.getJobData();
        UUID courtListId = jobData != null ? extractCourtListId(jobData) : null;
        String userId = extractUserId(jobData);

        // Fetch court list payload once for both CaTH and PDF processing (userId from CJSCPPUID header)
        CourtListPayload payload = null;
        if (jobData != null) {
            CourtListType listId = extractCourtListType(jobData);
            String courtCentreId = extractCourtCentreId(jobData);
            LocalDate publishDate = extractPublishDate(jobData);
            if (listId != null && courtCentreId != null && publishDate != null) {
                try {
                    payload = courtListQueryService.getCourtListPayload(
                            listId, courtCentreId, publishDate.toString(), publishDate.toString(), userId);
                } catch (Exception e) {
                    logger.error("Error fetching court list payload", e);
                }
            }
        }

        boolean cathSucceeded = false;
        try {
            queryAndSendCourtListToCaTH(executionInfo, payload);
            cathSucceeded = true;
        } catch (Exception e) {
            logger.error("Error querying or sending court list to CaTH", e);
            if (courtListId != null) {
                updateErrorMessage(courtListId, e, ErrorContext.PUBLISH);
            }
        }

        try {
            if (courtListId != null && cathSucceeded) {
                updateStatusToPublishSuccessful(courtListId);
            }
        } catch (Exception e) {
            logger.error("Error updating court list publish status to PUBLISH_SUCCESSFUL", e);
        }

        try {
            UUID fileId = generateAndUploadPdf(executionInfo, payload);
            if (fileId != null && courtListId != null) {
                updateFileIdAndLastUpdated(courtListId, fileId);
            }
        } catch (Exception e) {
            logger.error("Error generating and uploading PDF", e);
            if (courtListId != null) {
                updateErrorMessage(courtListId, e, ErrorContext.FILE);
            }
        }

        return executionInfo().from(executionInfo)
                .withExecutionStatus(COMPLETED)
                .build();
    }

    private void queryAndSendCourtListToCaTH(ExecutionInfo executionInfo, CourtListPayload payload) {
        if (payload == null) {
            logger.warn("Payload is null, cannot send court list to CaTH");
            return;
        }
        JsonObject jobData = executionInfo.getJobData();
        if (jobData == null) {
            return;
        }
        CourtListType listId = extractCourtListType(jobData);
        LocalDate publishDate = extractPublishDate(jobData);
        if (listId == null) {
            logger.warn("Missing listId (courtListType), cannot send court list to CaTH");
            return;
        }
        try {
            var courtListDocument = courtListQueryService.buildCourtListDocumentFromPayload(payload, listId);
            logger.info("Sending transformed court list document to CaTH endpoint");
            cathService.sendCourtListToCaTH(courtListDocument, listId, publishDate);
            logger.info("Successfully sent court list document to CaTH endpoint");
        } catch (Exception e) {
            logger.error("Error building document or sending court list to CaTH", e);
            throw new RuntimeException("Failed to send court list to CaTH: " + e.getMessage(), e);
        }
    }

    private UUID generateAndUploadPdf(ExecutionInfo executionInfo, CourtListPayload payload) {
        if (payload == null) {
            logger.warn("Payload is null, cannot generate PDF");
            return null;
        }
        JsonObject jobData = executionInfo.getJobData();
        if (jobData == null) {
            return null;
        }
        UUID courtListId = extractCourtListId(jobData);
        if (courtListId == null) {
            logger.warn("Missing courtListId for PDF generation");
            return null;
        }
        CourtListType listId = extractCourtListType(jobData);
        logger.info("Generating PDF for court list ID: {}", courtListId);
        try {
            UUID fileId = pdfHelper.generateAndUploadPdf(payload, courtListId, listId);
            logger.info("Successfully generated and uploaded PDF for court list ID: {}", courtListId);
            return fileId;
        } catch (Exception e) {
            logger.error("Error generating and uploading PDF for court list ID: {}", courtListId, e);
            throw new RuntimeException("Error generating and uploading PDF: " + e.getMessage(), e);
        }
    }

    private CourtListType extractCourtListType(JsonObject jobData) {
        try {
            return CourtListType.valueOf(jobData.getString(JobDataConstant.COURT_LIST_TYPE, "").toUpperCase());
        } catch (Exception e) {
            logger.warn("Could not extract listId (courtListType) from JsonObject", e);
            return null;
        }
    }

    private String extractCourtCentreId(JsonObject jobData) {
        try {
            return jobData.getString(JobDataConstant.COURT_CENTRE_ID, null);
        } catch (Exception e) {
            logger.warn("Could not extract courtCentreId from JsonObject", e);
            return null;
        }
    }

    private LocalDate extractPublishDate(JsonObject jobData) {
        if (jobData == null) {
            return null;
        }
        try {
            String value = jobData.getString(JobDataConstant.PUBLISH_DATE, null);
            if (value == null || value.isBlank()) {
                return null;
            }
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            logger.warn("Could not parse publishDate from JsonObject: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            logger.warn("Could not extract publishDate from JsonObject", e);
            return null;
        }
    }

    /**
     * Reads userId from jobData (CJSCPPUID from request). May be null if header was not sent.
     */
    private String extractUserId(JsonObject jobData) {
        if (jobData == null || !jobData.containsKey(JobDataConstant.USER_ID)) {
            return null;
        }
        try {
            String value = jobData.getString(JobDataConstant.USER_ID, null);
            return (value != null && !value.isBlank()) ? value : null;
        } catch (Exception e) {
            logger.warn("Could not extract userId from JsonObject", e);
            return null;
        }
    }

    private void updateStatusToPublishSuccessful(UUID courtListId) {
        CourtListStatusEntity existingCourtListPublishEntity = repository.getByCourtListId(courtListId);
        if (existingCourtListPublishEntity == null) {
            logger.warn("No record found with court list ID: {}", courtListId);
            return;
        }

        existingCourtListPublishEntity.setPublishStatus(Status.SUCCESSFUL);
        existingCourtListPublishEntity.setLastUpdated(Instant.now());
        repository.save(existingCourtListPublishEntity);
        logger.info("Successfully updated status to SUCCESSFUL for court list ID: {}", courtListId);
    }

    private void updateFileIdAndLastUpdated(UUID courtListId, UUID fileId) {
        CourtListStatusEntity existingCourtListPublishEntity = repository.getByCourtListId(courtListId);
        if (existingCourtListPublishEntity == null) {
            logger.warn("No record found with court list ID: {}", courtListId);
            return;
        }
        existingCourtListPublishEntity.setFileId(fileId);
        existingCourtListPublishEntity.setFileStatus(Status.SUCCESSFUL);
        existingCourtListPublishEntity.setFileErrorMessage(null);
        existingCourtListPublishEntity.setLastUpdated(Instant.now());
        repository.save(existingCourtListPublishEntity);
        logger.info("Successfully updated fileId and lastUpdated for court list ID: {}", courtListId);
    }

    private void updateErrorMessage(UUID courtListId, Exception e, ErrorContext context) {
        CourtListStatusEntity entity = repository.getByCourtListId(courtListId);
        if (entity == null) {
            logger.warn("No record found with court list ID: {}", courtListId);
            return;
        }
        String message = buildErrorMessage(e);
        if (context == ErrorContext.PUBLISH) {
            entity.setPublishErrorMessage(message);
            entity.setPublishStatus(Status.FAILED);
            logger.info("Saved publish error message for court list ID: {}", courtListId);
        } else {
            entity.setFileErrorMessage(message);
            entity.setFileStatus(Status.FAILED);
            logger.info("Saved file error message for court list ID: {}", courtListId);
        }
        entity.setLastUpdated(Instant.now());
        repository.save(entity);
    }

    private static String buildErrorMessage(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private UUID extractCourtListId(JsonObject jobData) {
        try {
            String courtListIdStr = jobData.getString(JobDataConstant.COURT_LIST_ID, null);
            return courtListIdStr != null ? UUID.fromString(courtListIdStr) : null;
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid UUID format for courtListId: {}", jobData.getString(JobDataConstant.COURT_LIST_ID, null), e);
            return null;
        } catch (Exception e) {
            logger.warn("Could not extract courtListId from JsonObject", e);
            return null;
        }
    }
}
