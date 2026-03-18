package uk.gov.hmcts.cp.cleanup;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import uk.gov.hmcts.cp.Application;
import uk.gov.hmcts.cp.domain.CourtListStatusEntity;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.openapi.model.Status;
import uk.gov.hmcts.cp.repositories.CourtListStatusRepository;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Integration test for CleanupJobService (used by GET /publish-status-cleanup): records older than the
 * retention period are purged from both court_list_publish_status and the Azure blob store.
 */
@SpringBootTest(
    classes = Application.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("retention-purge-test")
@Testcontainers
@Import(CleanupJobIntegrationTest.AzuriteTestConfig.class)
@TestPropertySource(properties = {
    "cleanup.enabled=true",
    "azure.storage.enabled=true"
})
class CleanupJobIntegrationTest {

    /** Provides BlobContainerClient for this test; use profile retention-purge-test so AzureIntegrationConfig (integration) is not loaded and we avoid duplicate bean. */
    @TestConfiguration
    @Profile("retention-purge-test")
    static class AzuriteTestConfig {
        @Bean
        BlobContainerClient blobContainerClient() {
            String endpoint = "http://localhost:" + AZURITE.getMappedPort(AZURITE_BLOB_PORT) + "/" + AZURITE_ACCOUNT_NAME;
            StorageSharedKeyCredential credential = new StorageSharedKeyCredential(AZURITE_ACCOUNT_NAME, AZURITE_ACCOUNT_KEY);
            BlobServiceClient serviceClient = new BlobServiceClientBuilder()
                    .endpoint(endpoint)
                    .credential(credential)
                    .buildClient();
            BlobContainerClient containerClient = serviceClient.getBlobContainerClient(CONTAINER_NAME);
            if (!containerClient.exists()) {
                containerClient.create();
            }
            return containerClient;
        }
    }

    /** Test uses 7-day retention (passed to service); records older than this are purged. */
    private static final int RETENTION_DAYS_IN_TEST = 7;
    private static final int DAYS_BEYOND_RETENTION = 10;
    private static final String PDF_EXTENSION = ".pdf";

    private static final String AZURITE_IMAGE = "mcr.microsoft.com/azure-storage/azurite:3.34.0";
    private static final int AZURITE_BLOB_PORT = 10000;
    private static final String AZURITE_ACCOUNT_NAME = "devstoreaccount1";
    private static final String AZURITE_ACCOUNT_KEY =
        "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";
    private static final String CONTAINER_NAME = "courtpublisher-blob-container";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("courtlistpublishing")
        .withUsername("courtlistpublishing")
        .withPassword("courtlistpublishing");

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> AZURITE = new GenericContainer<>(DockerImageName.parse(AZURITE_IMAGE))
        .withCommand("azurite-blob", "--blobHost", "0.0.0.0", "--skipApiVersionCheck")
        .withExposedPorts(AZURITE_BLOB_PORT);

    static {
        POSTGRES.start();
        AZURITE.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("azure.storage.account.name", () -> AZURITE_ACCOUNT_NAME);
        registry.add("azure.storage.account-key", () -> AZURITE_ACCOUNT_KEY);
        registry.add("azure.storage.container-name", () -> CONTAINER_NAME);
        registry.add("azure.storage.blob-endpoint", CleanupJobIntegrationTest::azuriteBlobEndpoint);
    }

    private static String azuriteBlobEndpoint() {
        return "http://localhost:" + AZURITE.getMappedPort(AZURITE_BLOB_PORT) + "/" + AZURITE_ACCOUNT_NAME;
    }

    @Autowired
    private CourtListStatusRepository repository;

    @Autowired
    private CleanupJobService cleanupJobService;

    @Autowired
    private BlobContainerClient blobContainerClient;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        if (!blobContainerClient.exists()) {
            blobContainerClient.create();
        }
    }

    @Test
    void cleanupOldData_shouldDeleteRecordsAndBlobs_afterRetentionPeriod() {
        UUID courtListId1 = UUID.randomUUID();
        UUID fileId1 = UUID.randomUUID();
        UUID courtListId2 = UUID.randomUUID();
        UUID fileId2 = UUID.randomUUID();
        Instant oldInstant = Instant.now().minus(DAYS_BEYOND_RETENTION, ChronoUnit.DAYS);

        repository.save(createEntity(courtListId1, fileId1, oldInstant));
        repository.save(createEntity(courtListId2, fileId2, oldInstant));
        uploadBlob(fileId1);
        uploadBlob(fileId2);

        assertRecordExists(courtListId1);
        assertRecordExists(courtListId2);
        assertBlobExists(fileId1);
        assertBlobExists(fileId2);

        cleanupJobService.cleanupOldData(RETENTION_DAYS_IN_TEST, null);

        assertRecordDeleted(courtListId1);
        assertRecordDeleted(courtListId2);
        assertBlobDeleted(fileId1);
        assertBlobDeleted(fileId2);
    }

    @Test
    void cleanupOldData_shouldNotDeleteRecentRecords_orTheirBlobs() {
        UUID oldCourtListId = UUID.randomUUID();
        UUID oldFileId = UUID.randomUUID();
        UUID recentCourtListId = UUID.randomUUID();
        UUID recentFileId = UUID.randomUUID();

        repository.save(createEntity(oldCourtListId, oldFileId, Instant.now().minus(DAYS_BEYOND_RETENTION, ChronoUnit.DAYS)));
        repository.save(createEntity(recentCourtListId, recentFileId, Instant.now()));
        uploadBlob(oldFileId);
        uploadBlob(recentFileId);

        cleanupJobService.cleanupOldData(RETENTION_DAYS_IN_TEST, null);

        assertRecordDeleted(oldCourtListId);
        assertBlobDeleted(oldFileId);
        assertRecordExists(recentCourtListId);
        assertBlobExists(recentFileId);
    }

    @Test
    void cleanupOldData_shouldNotDeleteRecord_whenBlobDoesNotExist() {
        UUID courtListId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        repository.save(createEntity(courtListId, fileId, Instant.now().minus(DAYS_BEYOND_RETENTION, ChronoUnit.DAYS)));
        // Blob not uploaded

        cleanupJobService.cleanupOldData(RETENTION_DAYS_IN_TEST, null);

        assertRecordExists(courtListId);
    }

    private void assertRecordExists(UUID courtListId) {
        assertThat(repository.findById(courtListId)).isPresent();
    }

    private void assertRecordDeleted(UUID courtListId) {
        assertThat(repository.findById(courtListId)).isEmpty();
    }

    private void assertBlobExists(UUID fileId) {
        assertThat(blobContainerClient.getBlobClient(blobName(fileId)).exists()).isTrue();
    }

    private void assertBlobDeleted(UUID fileId) {
        assertThat(blobContainerClient.getBlobClient(blobName(fileId)).exists()).isFalse();
    }

    private static CourtListStatusEntity createEntity(UUID courtListId, UUID fileId, Instant lastUpdated) {
        CourtListStatusEntity entity = new CourtListStatusEntity(
            courtListId,
            UUID.randomUUID(),
            Status.SUCCESSFUL,
            Status.SUCCESSFUL,
            CourtListType.STANDARD,
            lastUpdated
        );
        entity.setPublishDate(LocalDate.now());
        entity.setFileId(fileId);
        return entity;
    }

    private static String blobName(UUID fileId) {
        return fileId + PDF_EXTENSION;
    }

    private void uploadBlob(UUID fileId) {
        byte[] content = ("pdf-content-" + fileId).getBytes(StandardCharsets.UTF_8);
        blobContainerClient.getBlobClient(blobName(fileId)).upload(
            new ByteArrayInputStream(content), content.length, true
        );
    }
}
