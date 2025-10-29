package uk.gov.hmcts.cp.config;

import com.azure.storage.blob.BlobContainerClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import static org.mockito.Mockito.mock;

@TestConfiguration
@Profile("integration")
public class IntegrationTestConfig {

    @Bean
    @Primary
    public BlobContainerClient blobContainerClient() {
        return mock(BlobContainerClient.class);
    }
}
