package uk.gov.hmcts.cp.services;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.common.StorageSharedKeyCredential;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.UUID;

import static java.lang.String.format;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "azure.storage.enabled", havingValue = "true", matchIfMissing = false)
public class CourtListPublisherBlobClientService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CourtListPublisherBlobClientService.class);

    private final BlobContainerClient blobContainerClient;

    @Value("${azure.storage.account.name}")
    private String storageAccountName;

    @Value("${azure.storage.account-key:}")
    private String storageAccountKey;

    @Value("${azure.storage.sas-url-expiry-minutes:120}")
    private long sasUrlExpiryInMinutes;

    /**
     * Uploads a PDF file to Azure Blob Storage and returns a SAS URL with expiry time
     */
    public String uploadPdfAndGenerateSasUrl(InputStream fileInputStream, long fileSize, String blobName) {
        try {
            LOGGER.info("Uploading PDF file {} to container {}", blobName, blobContainerClient.getBlobContainerName());
            
            // Get or create blob client
            BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
            
            // Set content type for PDF
            BlobHttpHeaders headers = new BlobHttpHeaders()
                    .setContentType("application/pdf");
            
            // Upload the file with overwrite enabled
            blobClient.upload(fileInputStream, fileSize, true);
            blobClient.setHttpHeaders(headers);
            
            LOGGER.info("Successfully uploaded PDF file: {}", blobName);
            
            // Generate SAS URL with expiry using account-level key
            String sasUrl = generateSasUrl(blobClient, sasUrlExpiryInMinutes);
            
            LOGGER.info("Generated SAS URL for blob: {} with expiry of {} minutes", blobName, sasUrlExpiryInMinutes);
            return sasUrl;
            
        } catch (Exception e) {
            LOGGER.error("Error uploading PDF file {} to Azure Blob Storage", blobName, e);
            throw new RuntimeException(
                String.format("Azure storage error while uploading PDF file: %s. Error: %s", 
                    blobName, e.getMessage()), e);
        }
    }

    /**
     * Uploads a PDF file with auto-generated blob name and returns SAS URL
     */
    public String uploadPdfAndGenerateSasUrlWithAutoName(InputStream fileInputStream, long fileSize, String folderPath) {
        String blobName = generateBlobName(folderPath);
        return uploadPdfAndGenerateSasUrl(fileInputStream, fileSize, blobName);
    }

    /**
     * Generates a SAS URL for a blob with specified expiry time using account-level key
     */
    private String generateSasUrl(BlobClient blobClient, long expiryInMinutes) {
        if (storageAccountKey == null || storageAccountKey.isEmpty()) {
            throw new IllegalStateException(
                "Storage account key is required for SAS generation. " +
                "Set azure.storage.account-key property or AZURE_STORAGE_ACCOUNT_KEY environment variable.");
        }

        // Set expiry time
        OffsetDateTime expiryTime = OffsetDateTime.now().plusMinutes(expiryInMinutes);
        
        // Set permissions for the SAS token (read permission)
        BlobSasPermission sasPermission = new BlobSasPermission()
                .setReadPermission(true);
        
        // Create SAS signature values
        BlobServiceSasSignatureValues sasSignatureValues = new BlobServiceSasSignatureValues(
                expiryTime,
                sasPermission
        );

        // Create storage shared key credential
        StorageSharedKeyCredential credential = new StorageSharedKeyCredential(
                storageAccountName, 
                storageAccountKey
        );
        
        // Create a new BlobClient with account key credential for SAS generation
        // This is necessary because the original blobClient uses managed identity which doesn't support SAS generation
        BlobClient sasBlobClient = new BlobClientBuilder()
                .endpoint(format("https://%s.blob.core.windows.net", storageAccountName))
                .containerName(blobContainerClient.getBlobContainerName())
                .blobName(blobClient.getBlobName())
                .credential(credential)
                .buildClient();
        
        // Generate SAS token using the credential-enabled blob client
        String sasToken = sasBlobClient.generateSas(sasSignatureValues);
        
        // Return the full URL with SAS token
        return blobClient.getBlobUrl() + "?" + sasToken;
    }


    private String generateBlobName(String folderPath) {
        String fileName = UUID.randomUUID() + "." + "pdf";
        if (folderPath != null && !folderPath.isEmpty()) {
            return folderPath + "/" + fileName;
        }
        return fileName;
    }
}
