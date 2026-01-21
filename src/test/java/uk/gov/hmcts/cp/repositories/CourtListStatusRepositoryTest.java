package uk.gov.hmcts.cp.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import uk.gov.hmcts.cp.domain.CourtListStatusEntity;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.openapi.model.PublishStatus;

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
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CourtListStatusRepositoryTest {

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
        PublishStatus publishStatus = PublishStatus.PUBLISH_REQUESTED;
        CourtListType courtListType = CourtListType.PUBLIC;
        Instant lastUpdated = Instant.now();

        CourtListStatusEntity entity = new CourtListStatusEntity(
                courtListId,
                courtCentreId,
                publishStatus,
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
        PublishStatus publishStatus = PublishStatus.PUBLISH_REQUESTED;
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
                courtListType,
                lastUpdated
        );
        entity.setCourtListFileId(courtListFileId);
        entity.setFileName(fileName);
        entity.setErrorMessage(errorMessage);
        entity.setPublishDate(publishDate);

        // When
        CourtListStatusEntity savedEntity = repository.save(entity);
        entityManager.flush();
        entityManager.clear();

        // Then
        CourtListStatusEntity retrievedEntity = repository.findById(courtListId).orElseThrow();
        assertThat(retrievedEntity.getCourtListFileId()).isEqualTo(courtListFileId);
        assertThat(retrievedEntity.getFileName()).isEqualTo(fileName);
        assertThat(retrievedEntity.getErrorMessage()).isNull();
        assertThat(retrievedEntity.getPublishDate()).isEqualTo(publishDate);
    }

    @Test
    void findById_shouldReturnEntity_whenEntityExists() {
        // Given
        UUID courtListId = UUID.randomUUID();
        UUID courtCentreId = UUID.randomUUID();
        PublishStatus publishStatus = PublishStatus.PUBLISH_REQUESTED;
        CourtListType courtListType = CourtListType.PUBLIC;
        Instant lastUpdated = Instant.now();

        CourtListStatusEntity entity = new CourtListStatusEntity(
                courtListId,
                courtCentreId,
                publishStatus,
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
        PublishStatus publishStatus = PublishStatus.PUBLISH_REQUESTED;
        CourtListType courtListType = CourtListType.STANDARD;
        Instant lastUpdated = Instant.now();

        CourtListStatusEntity entity = new CourtListStatusEntity(
                courtListId,
                courtCentreId,
                publishStatus,
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
                PublishStatus.PUBLISH_REQUESTED,
                CourtListType.STANDARD,
                Instant.now()
        );
        CourtListStatusEntity entity2 = new CourtListStatusEntity(
                courtListId2,
                courtCentreId,
                PublishStatus.PUBLISH_REQUESTED,
                CourtListType.STANDARD,
                Instant.now()
        );
        CourtListStatusEntity entity3 = new CourtListStatusEntity(
                courtListId3,
                UUID.randomUUID(), // Different court centre
                PublishStatus.PUBLISH_REQUESTED,
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
        PublishStatus publishStatus = PublishStatus.PUBLISH_REQUESTED;
        UUID courtListId1 = UUID.randomUUID();
        UUID courtListId2 = UUID.randomUUID();

        CourtListStatusEntity entity1 = new CourtListStatusEntity(
                courtListId1,
                UUID.randomUUID(),
                publishStatus,
                CourtListType.STANDARD,
                Instant.now()
        );
        CourtListStatusEntity entity2 = new CourtListStatusEntity(
                courtListId2,
                UUID.randomUUID(),
                publishStatus,
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
        PublishStatus publishStatus = PublishStatus.PUBLISH_REQUESTED;
        UUID courtListId1 = UUID.randomUUID();
        UUID courtListId2 = UUID.randomUUID();

        CourtListStatusEntity entity1 = new CourtListStatusEntity(
                courtListId1,
                courtCentreId,
                publishStatus,
                CourtListType.FINAL,
                Instant.now()
        );
        CourtListStatusEntity entity2 = new CourtListStatusEntity(
                courtListId2,
                courtCentreId,
                publishStatus,
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
        PublishStatus originalStatus = PublishStatus.PUBLISH_REQUESTED;
        PublishStatus newStatus = PublishStatus.PUBLISH_SUCCESSFUL;
        Instant lastUpdated = Instant.now();

        CourtListStatusEntity entity = new CourtListStatusEntity(
                courtListId,
                courtCentreId,
                originalStatus,
                CourtListType.ONLINE_PUBLIC,
                lastUpdated
        );
        entityManager.persistAndFlush(entity);
        entityManager.clear();

        // When
        CourtListStatusEntity existingEntity = repository.findById(courtListId).orElseThrow();
        existingEntity.setPublishStatus(newStatus);
        existingEntity.setFileName("updated-file.pdf");
        existingEntity.setErrorMessage(null);
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
        PublishStatus publishStatus = PublishStatus.PUBLISH_REQUESTED;
        CourtListType courtListType = CourtListType.DRAFT;
        Instant lastUpdated = Instant.now();

        CourtListStatusEntity entity = new CourtListStatusEntity(
                courtListId,
                courtCentreId,
                publishStatus,
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
                PublishStatus.PUBLISH_REQUESTED,
                CourtListType.FINAL,
                Instant.now()
        );
        CourtListStatusEntity entity2 = new CourtListStatusEntity(
                courtListId2,
                UUID.randomUUID(),
                PublishStatus.PUBLISH_REQUESTED,
                CourtListType.ONLINE_PUBLIC,
                Instant.now()
        );
        CourtListStatusEntity entity3 = new CourtListStatusEntity(
                courtListId3,
                UUID.randomUUID(),
                PublishStatus.PUBLISH_REQUESTED,
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
                courtCentreId, PublishStatus.PUBLISH_FAILED);

        // Then
        assertThat(foundEntities).isEmpty();
    }
}
