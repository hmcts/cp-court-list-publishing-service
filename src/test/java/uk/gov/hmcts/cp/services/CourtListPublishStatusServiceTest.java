package uk.gov.hmcts.cp.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.hmcts.cp.domain.CourtListPublishStatusEntity;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishResponse;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.openapi.model.PublishStatus;
import uk.gov.hmcts.cp.repositories.CourtListPublishStatusRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CourtListPublishStatusServiceTest {

    @Mock
    private CourtListPublishStatusRepository repository;

    @InjectMocks
    private CourtListPublishStatusService service;

    @Test
    void getByCourtListId_shouldReturnResponse_whenEntityExists() {
        // Given
        UUID courtListId = UUID.randomUUID();
        UUID courtCentreId = UUID.randomUUID();
        CourtListPublishStatusEntity entity = new CourtListPublishStatusEntity(
                courtListId,
                courtCentreId,
                null, // courtRoomId
                PublishStatus.PUBLISH_REQUESTED,
                CourtListType.PUBLIC,
                Instant.now()
        );

        when(repository.getByCourtListId(courtListId)).thenReturn(entity);

        // When
        CourtListPublishResponse result = service.getByCourtListId(courtListId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCourtListId()).isEqualTo(courtListId);
        assertThat(result.getCourtCentreId()).isEqualTo(courtCentreId);
        assertThat(result.getPublishStatus()).isEqualTo(PublishStatus.PUBLISH_REQUESTED);
        assertThat(result.getCourtListType()).isEqualTo(CourtListType.PUBLIC);
        verify(repository).getByCourtListId(courtListId);
    }

    @Test
    void getByCourtListId_shouldThrowResponseStatusException_whenEntityDoesNotExist() {
        // Given
        UUID courtListId = UUID.randomUUID();

        when(repository.getByCourtListId(courtListId)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> service.getByCourtListId(courtListId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException ex = (ResponseStatusException) exception;
                    assertThat(ex.getStatusCode().value()).isEqualTo(404);
                });

        verify(repository).getByCourtListId(courtListId);
    }

    @Test
    void getByCourtListId_shouldThrowResponseStatusException_whenCourtListIdIsNull() {
        // When & Then
        assertThatThrownBy(() -> service.getByCourtListId(null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException ex = (ResponseStatusException) exception;
                    assertThat(ex.getStatusCode().value()).isEqualTo(400);
                });

        verify(repository, never()).getByCourtListId(any());
    }

    @Test
    void createOrUpdate_shouldCreateNewEntity_whenEntityDoesNotExist() {
        // Given
        UUID courtListId = UUID.randomUUID();
        UUID courtCentreId = UUID.randomUUID();
        PublishStatus publishStatus = PublishStatus.PUBLISH_REQUESTED;
        CourtListType courtListType = CourtListType.STANDARD;

        when(repository.getByCourtListId(courtListId)).thenReturn(null);
        when(repository.save(any(CourtListPublishStatusEntity.class))).thenAnswer(invocation -> invocation.<CourtListPublishStatusEntity>getArgument(0));

        // When
        CourtListPublishResponse result = service.createOrUpdate(
                courtListId, courtCentreId, publishStatus, courtListType);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCourtListId()).isEqualTo(courtListId);
        assertThat(result.getCourtCentreId()).isEqualTo(courtCentreId);
        assertThat(result.getPublishStatus()).isEqualTo(publishStatus);
        assertThat(result.getCourtListType()).isEqualTo(courtListType);
        assertThat(result.getLastUpdated()).isNotNull();
        verify(repository).getByCourtListId(courtListId);
        verify(repository).save(any(CourtListPublishStatusEntity.class));
    }

    @Test
    void createOrUpdate_shouldUpdateExistingEntity_whenEntityExists() {
        // Given
        UUID courtListId = UUID.randomUUID();
        UUID courtCentreId = UUID.randomUUID();
        PublishStatus newPublishStatus = PublishStatus.PUBLISH_REQUESTED;
        CourtListType newCourtListType = CourtListType.ALPHABETICAL;

        CourtListPublishStatusEntity existingEntity = new CourtListPublishStatusEntity(
                courtListId,
                UUID.randomUUID(),
                null, // courtRoomId
                PublishStatus.PUBLISH_REQUESTED,
                CourtListType.FINAL,
                Instant.now()
        );

        when(repository.getByCourtListId(courtListId)).thenReturn(existingEntity);
        when(repository.save(existingEntity)).thenReturn(existingEntity);

        // When
        CourtListPublishResponse result = service.createOrUpdate(
                courtListId, courtCentreId, newPublishStatus, newCourtListType);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCourtListId()).isEqualTo(courtListId);
        assertThat(result.getCourtCentreId()).isEqualTo(courtCentreId);
        assertThat(result.getPublishStatus()).isEqualTo(newPublishStatus);
        assertThat(result.getCourtListType()).isEqualTo(newCourtListType);
        assertThat(result.getLastUpdated()).isNotNull();
        verify(repository).getByCourtListId(courtListId);
        verify(repository).save(existingEntity);
    }

    @Test
    void createOrUpdate_shouldThrowResponseStatusException_whenCourtListIdIsNull() {
        // When & Then
        assertThatThrownBy(() -> service.createOrUpdate(
                null, UUID.randomUUID(), PublishStatus.PUBLISH_REQUESTED, CourtListType.FINAL))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException ex = (ResponseStatusException) exception;
                    assertThat(ex.getStatusCode().value()).isEqualTo(400);
                });

        verify(repository, never()).getByCourtListId(any());
        verify(repository, never()).save(any());
    }

    @Test
    void createOrUpdate_shouldThrowResponseStatusException_whenCourtCentreIdIsNull() {
        // When & Then
        assertThatThrownBy(() -> service.createOrUpdate(
                UUID.randomUUID(), null, PublishStatus.PUBLISH_REQUESTED, CourtListType.DRAFT))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException ex = (ResponseStatusException) exception;
                    assertThat(ex.getStatusCode().value()).isEqualTo(400);
                });

        verify(repository, never()).save(any());
    }

    @Test
    void createOrUpdate_shouldThrowResponseStatusException_whenPublishStatusIsNull() {
        // When & Then
        assertThatThrownBy(() -> service.createOrUpdate(
                UUID.randomUUID(), UUID.randomUUID(), null, CourtListType.FIRM))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException ex = (ResponseStatusException) exception;
                    assertThat(ex.getStatusCode().value()).isEqualTo(400);
                });

        verify(repository, never()).save(any());
    }

    @Test
    void createOrUpdate_shouldThrowResponseStatusException_whenPublishStatusIsBlank() {
        // When & Then
        assertThatThrownBy(() -> service.createOrUpdate(
                UUID.randomUUID(), UUID.randomUUID(), null, CourtListType.PUBLIC))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException ex = (ResponseStatusException) exception;
                    assertThat(ex.getStatusCode().value()).isEqualTo(400);
                });

        verify(repository, never()).save(any());
    }

    @Test
    void createOrUpdate_shouldThrowResponseStatusException_whenCourtListTypeIsNull() {
        // When & Then
        assertThatThrownBy(() -> service.createOrUpdate(
                UUID.randomUUID(), UUID.randomUUID(), PublishStatus.PUBLISH_REQUESTED, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException ex = (ResponseStatusException) exception;
                    assertThat(ex.getStatusCode().value()).isEqualTo(400);
                });

        verify(repository, never()).save(any());
    }

    @Test
    void findByCourtCentreId_shouldReturnList_whenEntitiesExist() {
        // Given
        UUID courtCentreId = UUID.randomUUID();
        CourtListPublishStatusEntity entity1 = new CourtListPublishStatusEntity(
                UUID.randomUUID(),
                courtCentreId,
                null, // courtRoomId
                PublishStatus.PUBLISH_REQUESTED,
                CourtListType.PUBLIC,
                Instant.now()
        );
        CourtListPublishStatusEntity entity2 = new CourtListPublishStatusEntity(
                UUID.randomUUID(),
                courtCentreId,
                null, // courtRoomId
                PublishStatus.PUBLISH_SUCCESSFUL,
                CourtListType.STANDARD,
                Instant.now()
        );

        when(repository.findByCourtCentreId(courtCentreId)).thenReturn(List.of(entity1, entity2));

        // When
        List<CourtListPublishResponse> result = service.findByCourtCentreId(courtCentreId);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(CourtListPublishResponse::getCourtCentreId)
                .containsExactlyInAnyOrder(courtCentreId, courtCentreId);
        assertThat(result).extracting(r -> r.getPublishStatus())
                .containsExactlyInAnyOrder(PublishStatus.PUBLISH_SUCCESSFUL, PublishStatus.PUBLISH_REQUESTED);
        verify(repository).findByCourtCentreId(courtCentreId);
    }

    @Test
    void findByCourtCentreId_shouldReturnLimitedTo10Records_whenMoreThan10EntitiesExist() {
        // Given
        UUID courtCentreId = UUID.randomUUID();
        List<CourtListPublishStatusEntity> entities = java.util.stream.IntStream.range(0, 15)
                .mapToObj(i -> new CourtListPublishStatusEntity(
                        UUID.randomUUID(),
                        courtCentreId,
                        null, // courtRoomId
                        PublishStatus.PUBLISH_REQUESTED,
                        CourtListType.STANDARD,
                        Instant.now()
                ))
                .toList();

        when(repository.findByCourtCentreId(courtCentreId)).thenReturn(entities);

        // When
        List<CourtListPublishResponse> result = service.findByCourtCentreId(courtCentreId);

        // Then
        assertThat(result).hasSize(10);
        verify(repository).findByCourtCentreId(courtCentreId);
    }

    @Test
    void findByCourtCentreId_shouldThrowResponseStatusException_whenCourtCentreIdIsNull() {
        // When & Then
        assertThatThrownBy(() -> service.findByCourtCentreId(null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException ex = (ResponseStatusException) exception;
                    assertThat(ex.getStatusCode().value()).isEqualTo(400);
                });

        verify(repository, never()).findByCourtCentreId(any());
    }

    @Test
    void findAll_shouldReturnAllEntities() {
        // Given
        CourtListPublishStatusEntity entity1 = new CourtListPublishStatusEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null, // courtRoomId
                PublishStatus.PUBLISH_REQUESTED,
                CourtListType.STANDARD,
                Instant.now()
        );
        CourtListPublishStatusEntity entity2 = new CourtListPublishStatusEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null, // courtRoomId
                PublishStatus.PUBLISH_SUCCESSFUL,
                CourtListType.PUBLIC,
                Instant.now()
        );

        when(repository.findAll()).thenReturn(List.of(entity1, entity2));

        // When
        List<CourtListPublishResponse> result = service.findAll();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(r -> r.getPublishStatus())
                .containsExactlyInAnyOrder(PublishStatus.PUBLISH_REQUESTED, PublishStatus.PUBLISH_SUCCESSFUL);
        assertThat(result).extracting(r -> r.getCourtListType())
                .containsExactlyInAnyOrder(CourtListType.fromValue("STANDARD"), CourtListType.fromValue("PUBLIC"));
        verify(repository).findAll();
    }
}

