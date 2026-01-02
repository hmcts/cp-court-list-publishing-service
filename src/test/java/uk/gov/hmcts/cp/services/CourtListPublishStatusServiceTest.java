package uk.gov.hmcts.cp.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.hmcts.cp.domain.CourtListPublishStatusEntity;
import uk.gov.hmcts.cp.dto.CourtListPublishResponse;
import uk.gov.hmcts.cp.repositories.CourtListPublishStatusRepository;

import java.time.LocalDateTime;
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
                "PUBLISHED",
                "DAILY",
                LocalDateTime.now()
        );

        when(repository.getByCourtListId(courtListId)).thenReturn(entity);

        // When
        CourtListPublishResponse result = service.getByCourtListId(courtListId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.courtListId()).isEqualTo(courtListId);
        assertThat(result.courtCentreId()).isEqualTo(courtCentreId);
        assertThat(result.publishStatus()).isEqualTo("PUBLISHED");
        assertThat(result.courtListType()).isEqualTo("DAILY");
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
        String publishStatus = "PUBLISHED";
        String courtListType = "DAILY";

        when(repository.getByCourtListId(courtListId)).thenReturn(null);
        when(repository.save(any(CourtListPublishStatusEntity.class))).thenAnswer(invocation -> invocation.<CourtListPublishStatusEntity>getArgument(0));

        // When
        CourtListPublishResponse result = service.createOrUpdate(
                courtListId, courtCentreId, publishStatus, courtListType);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.courtListId()).isEqualTo(courtListId);
        assertThat(result.courtCentreId()).isEqualTo(courtCentreId);
        assertThat(result.publishStatus()).isEqualTo(publishStatus);
        assertThat(result.courtListType()).isEqualTo(courtListType);
        assertThat(result.lastUpdated()).isNotNull();
        verify(repository).getByCourtListId(courtListId);
        verify(repository).save(any(CourtListPublishStatusEntity.class));
    }

    @Test
    void createOrUpdate_shouldUpdateExistingEntity_whenEntityExists() {
        // Given
        UUID courtListId = UUID.randomUUID();
        UUID courtCentreId = UUID.randomUUID();
        String newPublishStatus = "PENDING";
        String newCourtListType = "WEEKLY";

        CourtListPublishStatusEntity existingEntity = new CourtListPublishStatusEntity(
                courtListId,
                UUID.randomUUID(),
                "PUBLISHED",
                "DAILY",
                LocalDateTime.now()
        );

        when(repository.getByCourtListId(courtListId)).thenReturn(existingEntity);
        when(repository.save(existingEntity)).thenReturn(existingEntity);

        // When
        CourtListPublishResponse result = service.createOrUpdate(
                courtListId, courtCentreId, newPublishStatus, newCourtListType);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.courtListId()).isEqualTo(courtListId);
        assertThat(result.courtCentreId()).isEqualTo(courtCentreId);
        assertThat(result.publishStatus()).isEqualTo(newPublishStatus);
        assertThat(result.courtListType()).isEqualTo(newCourtListType);
        assertThat(result.lastUpdated()).isNotNull();
        verify(repository).getByCourtListId(courtListId);
        verify(repository).save(existingEntity);
    }

    @Test
    void createOrUpdate_shouldThrowResponseStatusException_whenCourtListIdIsNull() {
        // When & Then
        assertThatThrownBy(() -> service.createOrUpdate(
                null, UUID.randomUUID(), "PUBLISHED", "DAILY"))
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
                UUID.randomUUID(), null, "PUBLISHED", "DAILY"))
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
                UUID.randomUUID(), UUID.randomUUID(), null, "DAILY"))
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
                UUID.randomUUID(), UUID.randomUUID(), "   ", "DAILY"))
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
                UUID.randomUUID(), UUID.randomUUID(), "PUBLISHED", null))
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
                "PUBLISHED",
                "DAILY",
                LocalDateTime.now()
        );
        CourtListPublishStatusEntity entity2 = new CourtListPublishStatusEntity(
                UUID.randomUUID(),
                courtCentreId,
                "PENDING",
                "WEEKLY",
                LocalDateTime.now()
        );

        when(repository.findByCourtCentreId(courtCentreId)).thenReturn(List.of(entity1, entity2));

        // When
        List<CourtListPublishResponse> result = service.findByCourtCentreId(courtCentreId);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(CourtListPublishResponse::courtCentreId)
                .containsExactlyInAnyOrder(courtCentreId, courtCentreId);
        assertThat(result).extracting(CourtListPublishResponse::publishStatus)
                .containsExactlyInAnyOrder("PUBLISHED", "PENDING");
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
                        "PUBLISHED",
                        "DAILY",
                        LocalDateTime.now()
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
                "PUBLISHED",
                "DAILY",
                LocalDateTime.now()
        );
        CourtListPublishStatusEntity entity2 = new CourtListPublishStatusEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "PENDING",
                "WEEKLY",
                LocalDateTime.now()
        );

        when(repository.findAll()).thenReturn(List.of(entity1, entity2));

        // When
        List<CourtListPublishResponse> result = service.findAll();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(CourtListPublishResponse::publishStatus)
                .containsExactlyInAnyOrder("PUBLISHED", "PENDING");
        assertThat(result).extracting(CourtListPublishResponse::courtListType)
                .containsExactlyInAnyOrder("DAILY", "WEEKLY");
        verify(repository).findAll();
    }
}

