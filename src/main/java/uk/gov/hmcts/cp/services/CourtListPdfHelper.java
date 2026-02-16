package uk.gov.hmcts.cp.services;

import jakarta.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.taskmanager.domain.converter.JsonObjectConverter;

import java.io.IOException;
import java.util.UUID;

/**
 * Helper class for PDF generation operations related to court list publishing.
 * Extracted from PdfGenerationTask to be used by CourtListPublishTask.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "azure.storage.enabled", havingValue = "true", matchIfMissing = false)
public class CourtListPdfHelper {

    private final PdfGenerationService pdfGenerationService;
    private final JsonObjectConverter objectConverter;

    /**
     * Generates and uploads PDF for the given court list payload and court list ID.
     *
     * @param payload       the court list payload to generate PDF from
     * @param courtListId   the court list ID (used as file ID and blob name)
     * @param courtListType the court list type (ONLINE_PUBLIC or STANDARD) to select the template
     * @return the file ID of the uploaded PDF, or null if payload is null
     * @throws RuntimeException when PDF generation/upload fails so the caller can persist the error
     */
    public UUID generateAndUploadPdf(uk.gov.hmcts.cp.models.CourtListPayload payload, UUID courtListId, CourtListType courtListType) {
        if (payload == null) {
            log.warn("Payload is null, cannot generate PDF for court list ID: {}", courtListId);
            return null;
        }
        try {
            log.info("Generating and uploading PDF for court list ID: {}", courtListId);
            JsonObject payloadJson = objectConverter.convertFromObject(payload);
            UUID fileId = pdfGenerationService.generateAndUploadPdf(payloadJson, courtListId, courtListType);
            log.info("Successfully generated and uploaded PDF for court list ID: {}", courtListId);
            return fileId;
        } catch (IOException e) {
            log.error("Error generating and uploading PDF for court list ID: {}", courtListId, e);
            throw new RuntimeException("Failed to generate or upload PDF: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during PDF generation for court list ID: {}", courtListId, e);
            throw new RuntimeException("Failed to generate or upload PDF: " + e.getMessage(), e);
        }
    }
}
