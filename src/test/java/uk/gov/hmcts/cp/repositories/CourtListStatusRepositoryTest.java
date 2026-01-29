package uk.gov.hmcts.cp.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import uk.gov.hmcts.cp.domain.CourtListStatusEntity;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.openapi.model.Status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=true",
        "spring.jpa.properties.hibernate.connection.provider_disables_autocommit=false"
})
@Testcontainers
@AutoConfigureTestDatabase(replace = Replace.NONE)
class CourtListStatusRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("courtlistpublishing")
                    .withUsername("courtlistpublishing")
                    .withPassword("courtlistpublishing");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void dbProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CourtListStatusRepository repository;

    @AfterEach
    void clearData() {
        repository.deleteAll();
        entityManager.clear();
    }

    @Test
    void save_shouldPersistEntity_whenValidEntityProvided() {
        // Given
        UUID courtListId = UUID.randomUUID();
        UUID courtCentreId = UUID.randomUUID();
        Status publishStatus = Status.REQUESTED;
        CourtListType courtListType = CourtListType.PUBLIC;
        Instant lastUpdated = Instant.now();

        CourtListStatusEntity entity = new CourtListStatusEntity(
                courtListId,
                courtCentreId,
                publishStatus,
                Status.REQUESTED,
                courtListType,
                lastUpdated
        );

        // When
        CourtListStatusEntity savedEntity = repository.save(entity);
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(savedEntity).isNotNull();
        assertThat(savedEntity.getCourtListId()).isEqualTo(courtListId);
        assertThat(savedEntity.getCourtCentreId()).isEqualTo(courtCentreId);
        assertThat(savedEntity.getPublishStatus()).isEqualTo(publishStatus);
        assertThat(savedEntity.getCourtListType()).isEqualTo(courtListType);
        assertThat(savedEntity.getLastUpdated()).isEqualTo(lastUpdated);
    }

    @Test
    void save_shouldPersistEntityWithOptionalFields_whenAllFieldsProvided() {
        // Given
        UUID courtListId = UUID.randomUUID();
        UUID courtCentreId = UUID.randomUUID();
        Status publishStatus = Status.REQUESTED;
        CourtListType courtListType = CourtListType.PUBLIC;
        Instant lastUpdated = Instant.now();
        UUID courtListFileId = UUID.randomUUID();
        String fileName = "court-list-2024-01-15.pdf";
        String errorMessage = null;
        LocalDate publishDate = LocalDate.now();

        CourtListStatusEntity entity = new CourtListStatusEntity(
                courtListId,
                courtCentreId,
                publishStatus,
                Status.REQUESTED,
                courtListType,
                lastUpdated
        );
        entity.setCourtListFileId(courtListFileId);
        entity.setFileName(fileName);
        entity.setPublishErrorMessage(errorMessage);
        entity.setPublishDate(publishDate);

        // When
        CourtListStatusEntity savedEntity = repository.save(entity);
        entityManager.flush();
        entityManager.clear();

        // Then
        CourtListStatusEntity retrievedEntity = repository.findById(courtListId).orElseThrow();
        assertThat(retrievedEntity.getCourtListFileId()).isEqualTo(courtListFileId);
        assertThat(retrievedEntity.getFileName()).isEqualTo(fileName);
        assertThat(retrievedEntity.getPublishErrorMessage()).isNull();
        assertThat(retrievedEntity.getPublishDate()).isEqualTo(publishDate);
    }

    @Test
    void findById_shouldReturnEntity_whenEntityExists() {
        // Given
        UUID courtListId = UUID.randomUUID();
        UUID courtCentreId = UUID.randomUUID();
        Status publishStatus = Status.REQUESTED;
        CourtListType courtListType = CourtListType.PUBLIC;
        Instant lastUpdated = Instant.now();

        CourtListStatusEntity entity = new CourtListStatusEntity(
                courtListId,
                courtCentreId,
                publishStatus,
                Status.REQUESTED,
                courtListType,
                lastUpdated
        );
        entityManager.persistAndFlush(entity);
        entityManager.clear();

        // When
        Optional<CourtListStatusEntity> foundEntity = repository.findById(courtListId);

        // Then
        assertThat(foundEntity).isPresent();
        assertThat(foundEntity.get().getCourtListId()).isEqualTo(courtListId);
        assertThat(foundEntity.get().getCourtCentreId()).isEqualTo(courtCentreId);
        assertThat(foundEntity.get().getPublishStatus()).isEqualTo(publishStatus);
        assertThat(foundEntity.get().getCourtListType()).isEqualTo(courtListType);
    }

    @Test
    void findById_shouldReturnEmpty_whenEntityDoesNotExist() {
        // Given
        UUID nonExistentCourtListId = UUID.randomUUID();

        // When
        Optional<CourtListStatusEntity> foundEntity = repository.findById(nonExistentCourtListId);

        // Then
        assertThat(foundEntity).isEmpty();
    }

    @Test
    void getByCourtListId_shouldReturnEntity_whenEntityExists() {
        // Given
        UUID courtListId = UUID.randomUUID();
        UUID courtCentreId = UUID.randomUUID();
        Status publishStatus = Status.REQUESTED;
        CourtListType courtListType = CourtListType.STANDARD;
        Instant lastUpdated = Instant.now();

        CourtListStatusEntity entity = new CourtListStatusEntity(
                courtListId,
                courtCentreId,
                publishStatus,
                Status.REQUESTED,
                courtListType,
                lastUpdated
        );
        entityManager.persistAndFlush(entity);
        entityManager.clear();

        // When
        CourtListStatusEntity foundEntity = repository.getByCourtListId(courtListId);

        // Then
        assertThat(foundEntity).isNotNull();
        assertThat(foundEntity.getCourtListId()).isEqualTo(courtListId);
        assertThat(foundEntity.getCourtCentreId()).isEqualTo(courtCentreId);
        assertThat(foundEntity.getPublishStatus()).isEqualTo(publishStatus);
        assertThat(foundEntity.getCourtListType()).isEqualTo(courtListType);
    }

    @Test
    void findByCourtCentreId_shouldReturnAllEntities_whenMultipleEntitiesExistForSameCourtCentre() {
        // Given
        repository.deleteAll();
        UUID courtCentreId = UUID.randomUUID();
        UUID courtListId1 = UUID.randomUUID();
        UUID courtListId2 = UUID.randomUUID();
        UUID courtListId3 = UUID.randomUUID();

        CourtListStatusEntity entity1 = new CourtListStatusEntity(
                courtListId1,
                courtCentreId,
                Status.REQUESTED,
                Status.REQUESTED,
                CourtListType.STANDARD,
                Instant.now()
        );
        CourtListStatusEntity entity2 = new CourtListStatusEntity(
                courtListId2,
                courtCentreId,
                Status.REQUESTED,
                Status.REQUESTED,
                CourtListType.STANDARD,
                Instant.now()
        );
        CourtListStatusEntity entity3 = new CourtListStatusEntity(
                courtListId3,
                UUID.randomUUID(), // Different court centre
                Status.REQUESTED,
                Status.REQUESTED,
                CourtListType.STANDARD,
                Instant.now()
        );

        entityManager.persist(entity1);
        entityManager.persist(entity2);
        entityManager.persist(entity3);
        entityManager.flush();
        entityManager.clear();

        // When
        List<CourtListStatusEntity> foundEntities = repository.findByCourtCentreId(courtCentreId);

        // Then
        assertThat(foundEntities).hasSize(2);
        assertThat(foundEntities).extracting(CourtListStatusEntity::getCourtListId)
                .containsExactlyInAnyOrder(courtListId1, courtListId2);
        assertThat(foundEntities).extracting(CourtListStatusEntity::getCourtCentreId)
                .containsOnly(courtCentreId);
    }

    @Test
    void findByPublishStatus_shouldReturnAllEntities_whenMultipleEntitiesExistWithSameStatus() {
        // Given
        repository.deleteAll();
        Status publishStatus = Status.REQUESTED;
        UUID courtListId1 = UUID.randomUUID();
        UUID courtListId2 = UUID.randomUUID();

        CourtListStatusEntity entity1 = new CourtListStatusEntity(
                courtListId1,
                UUID.randomUUID(),
                publishStatus,
                Status.REQUESTED,
                CourtListType.STANDARD,
                Instant.now()
        );
        CourtListStatusEntity entity2 = new CourtListStatusEntity(
                courtListId2,
                UUID.randomUUID(),
                publishStatus,
                Status.REQUESTED,
                CourtListType.FINAL,
                Instant.now()
        );

        entityManager.persist(entity1);
        entityManager.persist(entity2);
        entityManager.flush();
        entityManager.clear();

        // When
        List<CourtListStatusEntity> foundEntities = repository.findByPublishStatus(publishStatus);

        // Then
        assertThat(foundEntities).hasSize(2);
        assertThat(foundEntities).extracting(CourtListStatusEntity::getCourtListId)
                .containsExactlyInAnyOrder(courtListId1, courtListId2);
        assertThat(foundEntities).extracting(CourtListStatusEntity::getPublishStatus)
                .containsOnly(publishStatus);
    }

    @Test
    void findByCourtCentreIdAndPublishStatus_shouldReturnMatchingEntities_whenBothCriteriaMatch() {
        // Given
        repository.deleteAll();
        UUID courtCentreId = UUID.randomUUID();
        Status publishStatus = Status.REQUESTED;
        UUID courtListId1 = UUID.randomUUID();
        UUID courtListId2 = UUID.randomUUID();

        CourtListStatusEntity entity1 = new CourtListStatusEntity(
                courtListId1,
                courtCentreId,
                publishStatus,
                Status.REQUESTED,
                CourtListType.FINAL,
                Instant.now()
        );
        CourtListStatusEntity entity2 = new CourtListStatusEntity(
                courtListId2,
                courtCentreId,
                publishStatus,
                Status.REQUESTED,
                CourtListType.FIRM,
                Instant.now()
        );

        entityManager.persist(entity1);
        entityManager.persist(entity2);
        entityManager.flush();
        entityManager.clear();

        // When
        List<CourtListStatusEntity> foundEntities = repository.findByCourtCentreIdAndPublishStatus(
                courtCentreId, publishStatus);

        // Then
        assertThat(foundEntities).hasSize(2);
        assertThat(foundEntities).extracting(CourtListStatusEntity::getCourtListId)
                .containsExactlyInAnyOrder(courtListId1, courtListId2);
        assertThat(foundEntities).extracting(CourtListStatusEntity::getCourtCentreId)
                .containsOnly(courtCentreId);
        assertThat(foundEntities).extracting(CourtListStatusEntity::getPublishStatus)
                .containsOnly(publishStatus);
    }

    @Test
    void update_shouldModifyEntity_whenEntityExists() {
        // Given
        UUID courtListId = UUID.randomUUID();
        UUID courtCentreId = UUID.randomUUID();
        Status originalStatus = Status.REQUESTED;
        Status newStatus = Status.SUCCESSFUL;
        Instant lastUpdated = Instant.now();

        CourtListStatusEntity entity = new CourtListStatusEntity(
                courtListId,
                courtCentreId,
                originalStatus,
                Status.REQUESTED,
                CourtListType.ONLINE_PUBLIC,
                lastUpdated
        );
        entityManager.persistAndFlush(entity);
        entityManager.clear();

        // When
        CourtListStatusEntity existingEntity = repository.findById(courtListId).orElseThrow();
        existingEntity.setPublishStatus(newStatus);
        existingEntity.setFileName("updated-file.pdf");
        existingEntity.setPublishErrorMessage(null);
        CourtListStatusEntity updatedEntity = repository.save(existingEntity);
        entityManager.flush();
        entityManager.clear();

        // Then
        CourtListStatusEntity retrievedEntity = repository.findById(courtListId).orElseThrow();
        assertThat(retrievedEntity.getPublishStatus()).isEqualTo(newStatus);
        assertThat(retrievedEntity.getFileName()).isEqualTo("updated-file.pdf");
        assertThat(retrievedEntity.getCourtListId()).isEqualTo(courtListId);
    }

    @Test
    void delete_shouldRemoveEntity_whenEntityExists() {
        // Given
        UUID courtListId = UUID.randomUUID();
        UUID courtCentreId = UUID.randomUUID();
        Status publishStatus = Status.REQUESTED;
        CourtListType courtListType = CourtListType.DRAFT;
        Instant lastUpdated = Instant.now();

        CourtListStatusEntity entity = new CourtListStatusEntity(
                courtListId,
                courtCentreId,
                publishStatus,
                Status.REQUESTED,
                courtListType,
                lastUpdated
        );
        entityManager.persistAndFlush(entity);
        entityManager.clear();

        // When
        repository.deleteById(courtListId);
        entityManager.flush();
        entityManager.clear();

        // Then
        Optional<CourtListStatusEntity> deletedEntity = repository.findById(courtListId);
        assertThat(deletedEntity).isEmpty();
    }

    @Test
    void findAll_shouldReturnAllEntities_whenMultipleEntitiesExist() {
        // Given
        repository.deleteAll();
        UUID courtListId1 = UUID.randomUUID();
        UUID courtListId2 = UUID.randomUUID();
        UUID courtListId3 = UUID.randomUUID();

        CourtListStatusEntity entity1 = new CourtListStatusEntity(
                courtListId1,
                UUID.randomUUID(),
                Status.REQUESTED,
                Status.REQUESTED,
                CourtListType.FINAL,
                Instant.now()
        );
        CourtListStatusEntity entity2 = new CourtListStatusEntity(
                courtListId2,
                UUID.randomUUID(),
                Status.REQUESTED,
                Status.REQUESTED,
                CourtListType.ONLINE_PUBLIC,
                Instant.now()
        );
        CourtListStatusEntity entity3 = new CourtListStatusEntity(
                courtListId3,
                UUID.randomUUID(),
                Status.REQUESTED,
                Status.REQUESTED,
                CourtListType.ONLINE_PUBLIC,
                Instant.now()
        );

        entityManager.persist(entity1);
        entityManager.persist(entity2);
        entityManager.persist(entity3);
        entityManager.flush();
        entityManager.clear();

        // When
        List<CourtListStatusEntity> allEntities = repository.findAll();

        // Then
        assertThat(allEntities).hasSize(3);
        assertThat(allEntities).extracting(CourtListStatusEntity::getCourtListId)
                .containsExactlyInAnyOrder(courtListId1, courtListId2, courtListId3);
    }

    @Test
    void findByCourtCentreId_shouldReturnEmptyList_whenNoEntitiesExistForCourtCentre() {
        // Given
        UUID nonExistentCourtCentreId = UUID.randomUUID();

        // When
        List<CourtListStatusEntity> foundEntities = repository.findByCourtCentreId(nonExistentCourtCentreId);

        // Then
        assertThat(foundEntities).isEmpty();
    }

    @Test
    void findByCourtCentreIdAndPublishStatus_shouldReturnEmptyList_whenNoMatchingEntitiesExist() {
        // Given
        UUID courtCentreId = UUID.randomUUID();
        // When
        List<CourtListStatusEntity> foundEntities = repository.findByCourtCentreIdAndPublishStatus(
                courtCentreId, Status.FAILED);

        // Then
        assertThat(foundEntities).isEmpty();
    }
}
