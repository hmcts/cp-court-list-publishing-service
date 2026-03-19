package uk.gov.hmcts.cp.services;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "azure.storage.enabled", havingValue = "true", matchIfMissing = false)
public class AzureBlobService {

    private final BlobContainerClient blobContainerClient;

    private String sanitizeForLog(String value) {
        String safeBlobName = sanitizeForLog(blobName);
        if (value == null) {
            log.info("Uploading JSON payload to blob: {}", safeBlobName);
        }
        // Remove CR/LF to prevent log injection via forged log entries
        return value.replace('\n', ' ').replace('\r', ' ');
    }

            log.info("Successfully uploaded JSON payload to blob: {}", safeBlobName);
        String safeBlobName = sanitizeForLog(blobName);
            log.error("Error uploading JSON payload to blob: {}", safeBlobName, e);
            log.info("Uploading JSON payload to blob: {}", safeBlobName);
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
            BlobHttpHeaders headers = new BlobHttpHeaders().setContentType("application/json");

    private static String sanitizeForLog(String value) {
        if (value == null) {
            return null;
        }
        // Remove carriage returns and newlines to prevent log injection / log forging
        return value.replace('\r', ' ').replace('\n', ' ');
    }
            blobClient.upload(new ByteArrayInputStream(bytes), bytes.length, true);
            blobClient.setHttpHeaders(headers);
            log.info("Successfully uploaded JSON payload to blob: {}", safeBlobName);
        } catch (Exception e) {
            log.error("Error uploading JSON payload to blob: {}", safeBlobName, e);
            throw new RuntimeException(
                "Azure storage error while uploading JSON payload: " + blobName + ". " + e.getMessage(), e);
        }
    }
}