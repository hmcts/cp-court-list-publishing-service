package uk.gov.hmcts.cp.config;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

/**
 * Only active when profile {@code integration} is active.
 */
@Slf4j
@Configuration
@Profile("integration")
@ConditionalOnProperty(name = "azure.storage.enabled", havingValue = "true", matchIfMissing = false)
public class AzureIntegrationConfig {

    @Value("${azure.storage.container-name}")
    private String containerName;

    @Value("${azure.storage.connection-string:}")
    private String connectionString;

    @Bean
    public BlobContainerClient blobContainerClient() {
        validateConfiguration();

        log.info("Using Azurite (Azure emulator) for integration tests via connection string");

        BlobServiceClient serviceClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();

        BlobContainerClient containerClient = serviceClient.getBlobContainerClient(containerName);

        if (!containerClient.exists()) {
            containerClient.create();
            log.info("Created Azurite blob container: {}", containerName);
        }

        return containerClient;
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(connectionString)) {
            throw new IllegalStateException(
                    "Azure storage connection string is required for integration profile (Azurite). " +
                    "Set AZURE_STORAGE_CONNECTION_STRING environment variable.");
        }
        if (!StringUtils.hasText(containerName)) {
            throw new IllegalStateException(
                    "Azure storage container name is required. Set azure.storage.container-name property.");
        }
    }
}
