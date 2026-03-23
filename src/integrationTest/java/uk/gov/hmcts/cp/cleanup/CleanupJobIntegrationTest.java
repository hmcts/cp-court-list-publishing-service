package uk.gov.hmcts.cp.cleanup;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.storage.blob.BlobContainerClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
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
import uk.gov.hmcts.cp.services.CaTHService;
import uk.gov.hmcts.cp.services.CourtListPublisherBlobClientService;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Integration test for CleanupJobService : records older than the
 * retention period are purged from both court_list_publish_status and the Azure blob store.
 */
@SpringBootTest(
    classes = Application.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("integration")
@Testcontainers
@Import(CleanupJobIntegrationTest.JwtFilterIntegrationSupportConfig.class)
@TestPropertySource(properties = {
    "azure.storage.enabled=true"
})
class CleanupJobIntegrationTest {

    /**
     * Supplies {@link PathMatcher} for {@code JWTFilter} in integration context only (no production/JWTConfig change).
     */
    @TestConfiguration
    static class JwtFilterIntegrationSupportConfig {
        @Bean
        PathMatcher pathMatcher() {
            return new AntPathMatcher();
        }
    }

    /** Test uses 7-day retention (passed to service); records older than this are purged. */
    private static final int RETENTION_DAYS_IN_TEST = 7;
    private static final int DAYS_BEYOND_RETENTION = 10;

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
        UUID courtListId2 = UUID.randomUUID();
        Instant oldInstant = Instant.now().minus(DAYS_BEYOND_RETENTION, ChronoUnit.DAYS);

        repository.save(createEntity(courtListId1, oldInstant));
        repository.save(createEntity(courtListId2, oldInstant));
        uploadPdfAndCathJson(courtListId1);
        uploadPdfAndCathJson(courtListId2);

        assertRecord(courtListId1);
        assertRecord(courtListId2);
        assertBlobs(courtListId1);
        assertBlobs(courtListId2);

        cleanupJobService.cleanupOldData(RETENTION_DAYS_IN_TEST);

        assertRecordDeleted(courtListId1);
        assertRecordDeleted(courtListId2);
        assertBlobsDeleted(courtListId1);
        assertBlobsDeleted(courtListId2);
    }

    @Test
    void cleanupOldData_shouldNotDeleteRecentRecords_orTheirBlobs() {
        UUID oldCourtListId = UUID.randomUUID();
        UUID recentCourtListId = UUID.randomUUID();

        repository.save(createEntity(oldCourtListId, Instant.now().minus(DAYS_BEYOND_RETENTION, ChronoUnit.DAYS)));
        repository.save(createEntity(recentCourtListId, Instant.now()));
        uploadPdfAndCathJson(oldCourtListId);
        uploadPdfAndCathJson(recentCourtListId);

        cleanupJobService.cleanupOldData(RETENTION_DAYS_IN_TEST);

        assertRecordDeleted(oldCourtListId);
        assertBlobsDeleted(oldCourtListId);
        assertRecord(recentCourtListId);
        assertBlobs(recentCourtListId);
    }

    @Test
    void cleanupOldData_shouldNotDeleteRecord_whenBlobDoesNotExist() {
        UUID courtListId = UUID.randomUUID();
        repository.save(createEntity(courtListId, Instant.now().minus(DAYS_BEYOND_RETENTION, ChronoUnit.DAYS)));
        // Blob not uploaded

        cleanupJobService.cleanupOldData(RETENTION_DAYS_IN_TEST);

        assertRecord(courtListId);
    }

    @Test
    void cleanupOldData_shouldDeleteRecord_whenOnlyPdfExists_cathJsonNotInStorage() {
        UUID courtListId = UUID.randomUUID();
        repository.save(createEntity(courtListId, Instant.now().minus(DAYS_BEYOND_RETENTION, ChronoUnit.DAYS)));
        byte[] pdfContent = ("pdf-only-" + courtListId).getBytes(StandardCharsets.UTF_8);
        blobContainerClient.getBlobClient(CourtListPublisherBlobClientService.buildPdfBlobName(courtListId)).upload(
            new ByteArrayInputStream(pdfContent), pdfContent.length, true
        );

        cleanupJobService.cleanupOldData(RETENTION_DAYS_IN_TEST);

        assertRecordDeleted(courtListId);
        assertThat(blobContainerClient.getBlobClient(CourtListPublisherBlobClientService.buildPdfBlobName(courtListId)).exists()).isFalse();
        assertThat(blobContainerClient.getBlobClient(CaTHService.buildBlobName(courtListId)).exists()).isFalse();
    }

    private void assertRecord(UUID courtListId) {
        assertThat(repository.findById(courtListId)).isPresent();
    }

    private void assertRecordDeleted(UUID courtListId) {
        assertThat(repository.findById(courtListId)).isEmpty();
    }

    private void assertBlobs(UUID courtListId) {
        assertThat(blobContainerClient.getBlobClient(CourtListPublisherBlobClientService.buildPdfBlobName(courtListId)).exists()).isTrue();
        assertThat(blobContainerClient.getBlobClient(CaTHService.buildBlobName(courtListId)).exists()).isTrue();
    }

    private void assertBlobsDeleted(UUID courtListId) {
        assertThat(blobContainerClient.getBlobClient(CourtListPublisherBlobClientService.buildPdfBlobName(courtListId)).exists()).isFalse();
        assertThat(blobContainerClient.getBlobClient(CaTHService.buildBlobName(courtListId)).exists()).isFalse();
    }

    private static CourtListStatusEntity createEntity(UUID courtListId, Instant lastUpdated) {
        CourtListStatusEntity entity = new CourtListStatusEntity(
            courtListId,
            UUID.randomUUID(),
            Status.SUCCESSFUL,
            Status.SUCCESSFUL,
            CourtListType.STANDARD,
            lastUpdated
        );
        entity.setPublishDate(LocalDate.now());
        entity.setFileId(courtListId);
        return entity;
    }

    private void uploadPdfAndCathJson(UUID courtListId) {
        byte[] pdfContent = ("pdf-content-" + courtListId).getBytes(StandardCharsets.UTF_8);
        blobContainerClient.getBlobClient(CourtListPublisherBlobClientService.buildPdfBlobName(courtListId)).upload(
            new ByteArrayInputStream(pdfContent), pdfContent.length, true
        );
        byte[] jsonContent = ("{\"courtListId\":\"" + courtListId + "\"}").getBytes(StandardCharsets.UTF_8);
        blobContainerClient.getBlobClient(CaTHService.buildBlobName(courtListId)).upload(
            new ByteArrayInputStream(jsonContent), jsonContent.length, true
        );
    }
}
