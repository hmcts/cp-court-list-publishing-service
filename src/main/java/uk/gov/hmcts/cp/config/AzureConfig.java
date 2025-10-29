package uk.gov.hmcts.cp.config;

import static java.lang.String.format;

import com.azure.core.util.ConfigurationBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AzureConfig {
    public static final String AZURE_CLIENT_ID = "AZURE_CLIENT_ID";
    public static final String AZURE_TENANT_ID = "AZURE_TENANT_ID";

    @Value("${azure.storage.container-name}")
    private String containerName;

    @Value( "${azure.client.id}")
    private String clientId;

    @Value("${azure.tenant.id}")
    private String tenantId;

    @Value("${azure.storage.account.name}")
    private String storageAccountName;


    @Bean
    public BlobContainerClient blobContainerClient() {
        BlobServiceClient serviceClient = createBlobServiceClient();

        BlobContainerClient containerClient = serviceClient.getBlobContainerClient(containerName);

        if (!containerClient.exists()) {
            containerClient.create();
        }

        return containerClient;
    }

    private BlobServiceClient createBlobServiceClient() {

        final com.azure.core.util.Configuration configuration = new ConfigurationBuilder()
                .putProperty(AZURE_CLIENT_ID, clientId)
                .putProperty(AZURE_TENANT_ID, tenantId)
                .build();

        return new BlobServiceClientBuilder()
                .endpoint(format("https://%s.blob.core.windows.net/", storageAccountName))
                .credential(new DefaultAzureCredentialBuilder()
                        .tenantId(tenantId)
                        .managedIdentityClientId(clientId)
                        .configuration(configuration)
                        .build())
                .buildClient();
    }
}