package uk.gov.hmcts.cp.config;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

import static java.lang.String.format;

/**
 * Only active when profile {@code integration} is active. Uses account name + key (e.g. Azurite).
 */
@Slf4j
@Configuration
@Profile("integration")
public class AzureIntegrationConfig {

    @Value("${azure.storage.container-name}")
    private String containerName;

    @Value("${azure.storage.account.name:}")
    private String storageAccountName;

    @Value("${azure.storage.account-key:}")
    private String storageAccountKey;

    @Value("${azure.storage.blob-endpoint:}")
    private String blobEndpoint;

    @Bean
    public BlobContainerClient blobContainerClient() {
        validateConfiguration();
        log.info("Using Azurite (Azure emulator) for integration tests");

        String endpoint = StringUtils.hasText(blobEndpoint)
            ? blobEndpoint
            : format("https://%s.blob.core.windows.net", storageAccountName);

        StorageSharedKeyCredential credential = new StorageSharedKeyCredential(
            storageAccountName,
            storageAccountKey
        );

        BlobServiceClient serviceClient = new BlobServiceClientBuilder()
                .endpoint(endpoint)
                .credential(credential)
                .buildClient();
        BlobContainerClient containerClient = serviceClient.getBlobContainerClient(containerName);
        if (!containerClient.exists()) {
            containerClient.create();
            log.info("Created Azurite blob container: {}", containerName);
        }
        return containerClient;
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(storageAccountName)) {
            throw new IllegalStateException(
                    "Azure storage account name is required for integration profile. " +
                    "Set AZURE_STORAGE_ACCOUNT_NAME (e.g. devstoreaccount1 for Azurite).");
        }
        if (!StringUtils.hasText(storageAccountKey)) {
            throw new IllegalStateException(
                    "Azure storage account key is required for integration profile. " +
                    "Set AZURE_STORAGE_ACCOUNT_KEY.");
        }
        if (!StringUtils.hasText(containerName)) {
            throw new IllegalStateException(
                    "Azure storage container name is required. Set azure.storage.container-name property.");
        }
    }
}
