package uk.gov.hmcts.cp.services;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.UUID;

/**
 * Uploads PDF files to Azure Blob Storage. Files are stored with name {fileId}.pdf
 * (under court-lists/). No SAS URL is generated; callers reference the file by fileId only.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "azure.storage.enabled", havingValue = "true", matchIfMissing = false)
public class CourtListPublisherBlobClientService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CourtListPublisherBlobClientService.class);
    private static final String COURT_LISTS_PREFIX = "court-lists";
    private static final String PDF_EXTENSION = ".pdf";

    private final BlobContainerClient blobContainerClient;

    /**
     * Uploads a PDF to blob storage with blob name court-lists/{fileId}.pdf.
     *
     * @param fileInputStream PDF content
     * @param fileSize        size in bytes
     * @param fileId          identifier for the file (e.g. court list ID); used as blob name (with .pdf)
     */
    public void uploadPdf(InputStream fileInputStream, long fileSize, UUID fileId) {
        String blobName = COURT_LISTS_PREFIX + "/" + fileId + PDF_EXTENSION;
        try {
            LOGGER.info("Uploading PDF {} to container {}", blobName, blobContainerClient.getBlobContainerName());
            BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
            BlobHttpHeaders headers = new BlobHttpHeaders().setContentType("application/pdf");
            blobClient.upload(fileInputStream, fileSize, true);
            blobClient.setHttpHeaders(headers);
            LOGGER.info("Successfully uploaded PDF: {}", blobName);
        } catch (Exception e) {
            LOGGER.error("Error uploading PDF {} to Azure Blob Storage", blobName, e);
            throw new RuntimeException(
                "Azure storage error while uploading PDF: " + blobName + ". " + e.getMessage(), e);
        }
    }
}
