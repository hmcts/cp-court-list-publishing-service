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

import java.time.Instant;
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
    private static final String USER_ID = "userId";
    private static final String MAKE_EXTERNAL_CALLS = "makeExternalCalls";

    private final CourtListStatusRepository repository;
    private final CourtListQueryService courtListQueryService;
    private final CaTHService cathService;
    private final CourtListPdfHelper pdfHelper;

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
        boolean makeExternalCalls = extractMakeExternalCalls(jobData);
        String userId = extractUserId(jobData);

        // Fetch court list payload once for both CaTH and PDF processing (userId from CJSCPPUID header)
        CourtListPayload payload = null;
        if (jobData != null) {
            CourtListType listId = extractCourtListType(jobData);
            String courtCentreId = extractCourtCentreId(jobData);
            String publishDate = extractPublishDate(jobData);
            if (listId != null && courtCentreId != null && publishDate != null && makeExternalCalls/*RM this next day*/) {
                try {
                    payload = courtListQueryService.getCourtListPayload(
                            listId, courtCentreId, publishDate, publishDate, userId);
                } catch (Exception e) {
                    logger.error("Error fetching court list payload", e);
                }
            }
        }

        try {
            queryAndSendCourtListToCaTH(executionInfo, payload, makeExternalCalls);
        } catch (Exception e) {
            logger.error("Error querying or sending court list to CaTH", e);
        }

        try {
            if (courtListId != null) {
                updateStatusToPublishSuccessful(courtListId);
            }
        } catch (Exception e) {
            logger.error("Error updating court list publish status to PUBLISH_SUCCESSFUL", e);
        }

        try {
            String sasUrl = makeExternalCalls
                ? generateAndUploadPdf(executionInfo, payload)
                : getMockBlobSasUrl(executionInfo);
            if (sasUrl != null && courtListId != null) {
                updateFileUrlAndLastUpdated(courtListId, sasUrl);
            }
        } catch (Exception e) {
            logger.error("Error generating and uploading PDF", e);
        }

        return executionInfo().from(executionInfo)
                .withExecutionStatus(COMPLETED)
                .build();
    }

    private String getMockBlobSasUrl(final ExecutionInfo executionInfo) {
        final CourtListType clt = extractCourtListType((executionInfo.getJobData()));

        if(clt == CourtListType.PUBLIC) {
            return "https://sastecourtlistpublisher.blob.core.windows.net/" +
                    "courtpublisher-blob-container/" +
                    "Online%20Public%20court%20list%20-%20Lavender%20Hill%20Magistrates'%20Court%2C%20All%20courtrooms%20-%2026-01-2026.pdf?st=2026-01-26T13:22:49Z&se=2026-12-31T21:37:49Z&" +
                    "si" +
                    "=" +
                    "ReadPolicyIdentifier&spr=https&sv=2024-11-04&sr=b&" +
                    "sig" +
                    "=" +
                    "%2F7gXu1fPVDnLpGfnC3xQZcGBL3LTUfKCZWfCYPOiCrQ%3D";
        }

        return "https://sastecourtlistpublisher.blob.core.windows.net/" +
                "courtpublisher-blob-container/" +
                "Standard%20court%20list%20-%20Lavender%20Hill%20Magistrates'%20Court%2C%20All%20courtrooms%20-%2026-01-2026.pdf?st=2026-01-26T13:25:46Z&se=2027-01-26T21:40:46Z&" +
                "si" +
                "=" +
                "ReadPolicyIdentifier&spr=https&sv=2024-11-04&sr=b&" +
                "sig" +
                "=" +
                "RtPqpntJQnM9h4jR8SUGDHP9502I4x%2BrOU9MNkF%2F4YQ%3D";


    }

    private void queryAndSendCourtListToCaTH(ExecutionInfo executionInfo, CourtListPayload payload, boolean makeExternalCalls) {
        if (payload == null) {
            logger.warn("Payload is null, cannot send court list to CaTH");
            return;
        }
        JsonObject jobData = executionInfo.getJobData();
        if (jobData == null) {
            return;
        }
        CourtListType listId = extractCourtListType(jobData);
        if (listId == null) {
            logger.warn("Missing listId (courtListType), cannot send court list to CaTH");
            return;
        }
        try {
            var courtListDocument = courtListQueryService.buildCourtListDocumentFromPayload(payload, listId);
            logger.info("Sending transformed court list document to CaTH endpoint");
            if (makeExternalCalls) {
                cathService.sendCourtListToCaTH(courtListDocument);
            } else {
                logger.info("Not calling CaTH as we are in mock mode");
            }
            logger.info("Successfully sent court list document to CaTH endpoint");
        } catch (Exception e) {
            logger.error("Error building document or sending court list to CaTH", e);
            throw new RuntimeException("Failed to send court list to CaTH: " + e.getMessage(), e);
        }
    }

    private String generateAndUploadPdf(ExecutionInfo executionInfo, CourtListPayload payload) {
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
        logger.info("Generating PDF for court list ID: {}", courtListId);
        try {
            String sasUrl = pdfHelper.generateAndUploadPdf(payload, courtListId);
            logger.info("Successfully generated and uploaded PDF for court list ID: {}", courtListId);
            return sasUrl;
        } catch (Exception e) {
            logger.error("Error generating and uploading PDF for court list ID: {}", courtListId, e);
            return null;
        }
    }

    private CourtListType extractCourtListType(JsonObject jobData) {
        try {
            return CourtListType.valueOf(jobData.getString(COURT_LIST_TYPE, "").toUpperCase());
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

    private String extractPublishDate(JsonObject jobData) {
        try {
            return jobData.getString("publishDate", null);
        } catch (Exception e) {
            logger.warn("Could not extract publishDate from JsonObject", e);
            return null;
        }
    }

    /**
     * Reads userId from jobData (CJSCPPUID from request). May be null if header was not sent.
     */
    private String extractUserId(JsonObject jobData) {
        if (jobData == null || !jobData.containsKey(USER_ID)) {
            return null;
        }
        try {
            String value = jobData.getString(USER_ID, null);
            return (value != null && !value.isBlank()) ? value : null;
        } catch (Exception e) {
            logger.warn("Could not extract userId from JsonObject", e);
            return null;
        }
    }

    /**
     * Reads makeExternalCalls from jobData (temporary param, to be removed by 2026-02-07). Default false.
     */
    private boolean extractMakeExternalCalls(JsonObject jobData) {
        if (jobData == null || !jobData.containsKey(MAKE_EXTERNAL_CALLS)) {
            return false;
        }
        try {
            return jobData.getBoolean(MAKE_EXTERNAL_CALLS);
        } catch (Exception e) {
            logger.warn("Could not extract makeExternalCalls from JsonObject, using false", e);
            return false;
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

    private void updateFileUrlAndLastUpdated(UUID courtListId, String fileUrl) {
        CourtListStatusEntity existingCourtListPublishEntity = repository.getByCourtListId(courtListId);
        if (existingCourtListPublishEntity == null) {
            logger.warn("No record found with court list ID: {}", courtListId);
            return;
        }

        existingCourtListPublishEntity.setFileUrl(fileUrl);
        existingCourtListPublishEntity.setFileId(courtListId);// same as courtListId .... azure file upload is atomic
        existingCourtListPublishEntity.setFileStatus(Status.SUCCESSFUL);
        existingCourtListPublishEntity.setLastUpdated(Instant.now());
        repository.save(existingCourtListPublishEntity);
        logger.info("Successfully updated fileUrl and lastUpdated for court list ID: {}", courtListId);
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
