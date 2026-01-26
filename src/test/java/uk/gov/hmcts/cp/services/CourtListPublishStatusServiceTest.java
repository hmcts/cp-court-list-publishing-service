package uk.gov.hmcts.cp.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.hmcts.cp.domain.CourtListStatusEntity;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishResponse;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.openapi.model.Status;
import uk.gov.hmcts.cp.repositories.CourtListStatusRepository;

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
    private CourtListStatusRepository repository;

    @InjectMocks
    private CourtListPublishStatusService service;

    @Test
    void getByCourtListId_shouldReturnResponse_whenEntityExists() {
        // Given
        UUID courtListId = UUID.randomUUID();
        UUID courtCentreId = UUID.randomUUID();
        CourtListStatusEntity entity = new CourtListStatusEntity(
                courtListId,
                courtCentreId,
                Status.REQUESTED,
                Status.REQUESTED,
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
        assertThat(result.getPublishStatus()).isEqualTo(Status.REQUESTED);
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
        Status publishStatus = Status.REQUESTED;
        CourtListType courtListType = CourtListType.STANDARD;

        when(repository.getByCourtListId(courtListId)).thenReturn(null);
        when(repository.save(any(CourtListStatusEntity.class))).thenAnswer(invocation -> invocation.<CourtListStatusEntity>getArgument(0));

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
        verify(repository).save(any(CourtListStatusEntity.class));
    }

    @Test
    void createOrUpdate_shouldUpdateExistingEntity_whenEntityExists() {
        // Given
        UUID courtListId = UUID.randomUUID();
        UUID courtCentreId = UUID.randomUUID();
        Status newPublishStatus = Status.REQUESTED;
        CourtListType newCourtListType = CourtListType.ALPHABETICAL;

        CourtListStatusEntity existingEntity = new CourtListStatusEntity(
                courtListId,
                UUID.randomUUID(),
                Status.REQUESTED,
                Status.REQUESTED,
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
                null, UUID.randomUUID(), Status.REQUESTED, CourtListType.FINAL))
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
                UUID.randomUUID(), null, Status.REQUESTED, CourtListType.DRAFT))
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
                UUID.randomUUID(), UUID.randomUUID(), Status.REQUESTED, null))
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
        CourtListStatusEntity entity1 = new CourtListStatusEntity(
                UUID.randomUUID(),
                courtCentreId,
                Status.REQUESTED,
                Status.REQUESTED,
                CourtListType.PUBLIC,
                Instant.now()
        );
        CourtListStatusEntity entity2 = new CourtListStatusEntity(
                UUID.randomUUID(),
                courtCentreId,
                Status.SUCCESSFUL,
                Status.REQUESTED,
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
        assertThat(result).extracting(CourtListPublishResponse::getPublishStatus)
                .containsExactlyInAnyOrder(Status.SUCCESSFUL, Status.REQUESTED);
        verify(repository).findByCourtCentreId(courtCentreId);
    }

    @Test
    void findByCourtCentreId_shouldReturnLimitedTo10Records_whenMoreThan10EntitiesExist() {
        // Given
        UUID courtCentreId = UUID.randomUUID();
        List<CourtListStatusEntity> entities = java.util.stream.IntStream.range(0, 15)
                .mapToObj(i -> new CourtListStatusEntity(
                        UUID.randomUUID(),
                        courtCentreId,
                        Status.REQUESTED,
                        Status.REQUESTED,
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
        CourtListStatusEntity entity1 = new CourtListStatusEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                Status.REQUESTED,
                Status.REQUESTED,
                CourtListType.STANDARD,
                Instant.now()
        );
        CourtListStatusEntity entity2 = new CourtListStatusEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                Status.SUCCESSFUL,
                Status.REQUESTED,
                CourtListType.PUBLIC,
                Instant.now()
        );

        when(repository.findAll()).thenReturn(List.of(entity1, entity2));

        // When
        List<CourtListPublishResponse> result = service.findAll();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(CourtListPublishResponse::getPublishStatus)
                .containsExactlyInAnyOrder(Status.REQUESTED, Status.SUCCESSFUL);
        assertThat(result).extracting(CourtListPublishResponse::getCourtListType)
                .containsExactlyInAnyOrder(CourtListType.fromValue("STANDARD"), CourtListType.fromValue("PUBLIC"));
        verify(repository).findAll();
    }
}

