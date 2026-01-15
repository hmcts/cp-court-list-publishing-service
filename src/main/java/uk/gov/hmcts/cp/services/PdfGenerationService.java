package uk.gov.hmcts.cp.services;

import jakarta.json.JsonObject;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * Service for generating PDF files from payload data and uploading to Azure Blob Storage.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "azure.storage.enabled", havingValue = "true", matchIfMissing = false)
public class PdfGenerationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfGenerationService.class);

    private final CourtListPublisherBlobClientService blobClientService;

    /**
     * Generates a PDF file from the provided payload data, uploads it to Azure Blob Storage, and returns the SAS URL
     */
    public String generateAndUploadPdf(JsonObject payload, UUID courtListId) throws IOException {
        LOGGER.info("Generating PDF for court list ID: {}", courtListId);
        
        // Generate PDF content
        ByteArrayOutputStream pdfOutputStream = new ByteArrayOutputStream();

        try {
            // Simple PDF header (minimal PDF structure)
            String pdfContent = generateSimplePdfContent(payload, courtListId);
            pdfOutputStream.write(pdfContent.getBytes());
            
            LOGGER.info("Successfully generated PDF for court list ID: {}, size: {} bytes", 
                    courtListId, pdfOutputStream.size());
        } catch (Exception e) {
            LOGGER.error("Error generating PDF for court list ID: {}", courtListId, e);
            throw new IOException("Failed to generate PDF: " + e.getMessage(), e);
        }
        
        // Generate blob name and upload to Azure
        String blobName = generateBlobName(courtListId, "court-lists");
        long pdfSize = pdfOutputStream.size();
        ByteArrayInputStream pdfInputStream = new ByteArrayInputStream(pdfOutputStream.toByteArray());
        
        // Upload to Azure Blob Storage and get SAS URL
        String sasUrl = blobClientService.uploadPdfAndGenerateSasUrl(pdfInputStream, pdfSize, blobName);
        
        LOGGER.info("Successfully uploaded PDF to Azure for court list ID: {}. SAS URL generated", courtListId);
        return sasUrl;
    }

    /**
     * Generates simple PDF content from payload
     */
    private String generateSimplePdfContent(JsonObject payload, UUID courtListId) {
        StringBuilder content = new StringBuilder();
        content.append("%PDF-1.4\n");
        content.append("1 0 obj\n");
        content.append("<<\n");
        content.append("/Type /Catalog\n");
        content.append(">>\n");
        content.append("endobj\n");
        content.append("\n");
        content.append("Court List ID: ").append(courtListId).append("\n");
        
        if (payload != null) {
            if (payload.containsKey("courtCentreId")) {
                content.append("Court Centre ID: ").append(payload.getString("courtCentreId")).append("\n");
            }
            if (payload.containsKey("courtListType")) {
                content.append("Court List Type: ").append(payload.getString("courtListType")).append("\n");
            }
        }
        
        content.append("\n%%EOF\n");
        return content.toString();
    }

    /**
     * Generates a blob name for the PDF file
     */
    private String generateBlobName(UUID courtListId, String folderPath) {
        String fileName = (courtListId != null ? courtListId.toString() : "null") + ".pdf";
        if (folderPath != null && !folderPath.isEmpty()) {
            return folderPath + "/" + fileName;
        }
        return fileName;
    }

    /**
     * Gets the size of the generated PDF
     */
    public long getPdfSize(ByteArrayOutputStream pdfOutputStream) {
        return pdfOutputStream.size();
    }
}
