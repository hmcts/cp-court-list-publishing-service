package uk.gov.hmcts.cp.services;

import jakarta.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
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
     * @param payload the court list payload to generate PDF from
     * @param courtListId the court list ID
     * @return the SAS URL of the uploaded PDF, or null if generation fails
     */
    public String generateAndUploadPdf(uk.gov.hmcts.cp.models.CourtListPayload payload, UUID courtListId) {
        if (payload == null) {
            log.warn("Payload is null, cannot generate PDF for court list ID: {}", courtListId);
            return null;
        }

        try {
            log.info("Generating and uploading PDF for court list ID: {}", courtListId);
            
            // Convert payload to JsonObject
            JsonObject payloadJson = objectConverter.convertFromObject(payload);
            
            // Generate and upload PDF
            String sasUrl = pdfGenerationService.generateAndUploadPdf(payloadJson, courtListId);
            
            log.info("Successfully generated and uploaded PDF for court list ID: {}. SAS URL generated", courtListId);
            return sasUrl;
        } catch (IOException e) {
            log.error("Error generating and uploading PDF for court list ID: {}", courtListId, e);
            return null;
        } catch (Exception e) {
            log.error("Unexpected error during PDF generation for court list ID: {}", courtListId, e);
            return null;
        }
    }
}
