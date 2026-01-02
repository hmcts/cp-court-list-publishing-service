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
        LocalDateTime now = LocalDateTime.now();

        when(repository.getByCourtListId(courtListId)).thenReturn(null);
        when(repository.save(any(CourtListPublishStatusEntity.class))).thenAnswer(invocation -> {
            CourtListPublishStatusEntity entity = invocation.getArgument(0);
            return entity;
        });

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
    void updateError_shouldUpdateErrorMessage_whenEntityExists() {
        // Given
        UUID courtListId = UUID.randomUUID();
        String errorMessage = "Failed to publish";

        CourtListPublishStatusEntity entity = new CourtListPublishStatusEntity(
                courtListId,
                UUID.randomUUID(),
                "FAILED",
                "DAILY",
                LocalDateTime.now()
        );

        when(repository.getByCourtListId(courtListId)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(entity);

        // When
        CourtListPublishResponse result = service.updateError(courtListId, errorMessage);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.errorMessage()).isEqualTo(errorMessage);
        assertThat(result.courtListId()).isEqualTo(courtListId);
        assertThat(result.lastUpdated()).isNotNull();
        verify(repository).getByCourtListId(courtListId);
        verify(repository).save(entity);
    }

    @Test
    void updateError_shouldThrowResponseStatusException_whenEntityDoesNotExist() {
        // Given
        UUID courtListId = UUID.randomUUID();

        when(repository.getByCourtListId(courtListId)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> service.updateError(courtListId, "Error message"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException ex = (ResponseStatusException) exception;
                    assertThat(ex.getStatusCode().value()).isEqualTo(404);
                });

        verify(repository).getByCourtListId(courtListId);
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
    void findByPublishStatus_shouldReturnList_whenEntitiesExist() {
        // Given
        String publishStatus = "PUBLISHED";
        CourtListPublishStatusEntity entity1 = new CourtListPublishStatusEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                publishStatus,
                "DAILY",
                LocalDateTime.now()
        );
        CourtListPublishStatusEntity entity2 = new CourtListPublishStatusEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                publishStatus,
                "WEEKLY",
                LocalDateTime.now()
        );

        when(repository.findByPublishStatus(publishStatus)).thenReturn(List.of(entity1, entity2));

        // When
        List<CourtListPublishResponse> result = service.findByPublishStatus(publishStatus);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(CourtListPublishResponse::publishStatus)
                .containsExactlyInAnyOrder(publishStatus, publishStatus);
        assertThat(result).extracting(CourtListPublishResponse::courtListType)
                .containsExactlyInAnyOrder("DAILY", "WEEKLY");
        verify(repository).findByPublishStatus(publishStatus);
    }

    @Test
    void findByPublishStatus_shouldThrowResponseStatusException_whenPublishStatusIsNull() {
        // When & Then
        assertThatThrownBy(() -> service.findByPublishStatus(null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException ex = (ResponseStatusException) exception;
                    assertThat(ex.getStatusCode().value()).isEqualTo(400);
                });

        verify(repository, never()).findByPublishStatus(any());
    }

    @Test
    void findByPublishStatus_shouldThrowResponseStatusException_whenPublishStatusIsBlank() {
        // When & Then
        assertThatThrownBy(() -> service.findByPublishStatus("   "))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException ex = (ResponseStatusException) exception;
                    assertThat(ex.getStatusCode().value()).isEqualTo(400);
                });

        verify(repository, never()).findByPublishStatus(any());
    }

    @Test
    void findByCourtCentreIdAndPublishStatus_shouldReturnList_whenEntitiesExist() {
        // Given
        UUID courtCentreId = UUID.randomUUID();
        String publishStatus = "PUBLISHED";
        CourtListPublishStatusEntity entity = new CourtListPublishStatusEntity(
                UUID.randomUUID(),
                courtCentreId,
                publishStatus,
                "DAILY",
                LocalDateTime.now()
        );

        when(repository.findByCourtCentreIdAndPublishStatus(courtCentreId, publishStatus))
                .thenReturn(List.of(entity));

        // When
        List<CourtListPublishResponse> result = service.findByCourtCentreIdAndPublishStatus(
                courtCentreId, publishStatus);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).courtCentreId()).isEqualTo(courtCentreId);
        assertThat(result.get(0).publishStatus()).isEqualTo(publishStatus);
        verify(repository).findByCourtCentreIdAndPublishStatus(courtCentreId, publishStatus);
    }

    @Test
    void findByCourtCentreIdAndPublishStatus_shouldThrowResponseStatusException_whenCourtCentreIdIsNull() {
        // When & Then
        assertThatThrownBy(() -> service.findByCourtCentreIdAndPublishStatus(null, "PUBLISHED"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException ex = (ResponseStatusException) exception;
                    assertThat(ex.getStatusCode().value()).isEqualTo(400);
                });

        verify(repository, never()).findByCourtCentreIdAndPublishStatus(any(), any());
    }

    @Test
    void findByCourtCentreIdAndPublishStatus_shouldThrowResponseStatusException_whenPublishStatusIsNull() {
        // When & Then
        assertThatThrownBy(() -> service.findByCourtCentreIdAndPublishStatus(UUID.randomUUID(), null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException ex = (ResponseStatusException) exception;
                    assertThat(ex.getStatusCode().value()).isEqualTo(400);
                });

        verify(repository, never()).findByCourtCentreIdAndPublishStatus(any(), any());
    }

    @Test
    void deleteByCourtListId_shouldDeleteEntity_whenEntityExists() {
        // Given
        UUID courtListId = UUID.randomUUID();

        when(repository.existsById(courtListId)).thenReturn(true);

        // When
        service.deleteByCourtListId(courtListId);

        // Then
        verify(repository).existsById(courtListId);
        verify(repository).deleteById(courtListId);
    }

    @Test
    void deleteByCourtListId_shouldThrowResponseStatusException_whenEntityDoesNotExist() {
        // Given
        UUID courtListId = UUID.randomUUID();

        when(repository.existsById(courtListId)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> service.deleteByCourtListId(courtListId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException ex = (ResponseStatusException) exception;
                    assertThat(ex.getStatusCode().value()).isEqualTo(404);
                });

        verify(repository).existsById(courtListId);
        verify(repository, never()).deleteById(any());
    }

    @Test
    void deleteByCourtListId_shouldThrowResponseStatusException_whenCourtListIdIsNull() {
        // When & Then
        assertThatThrownBy(() -> service.deleteByCourtListId(null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException ex = (ResponseStatusException) exception;
                    assertThat(ex.getStatusCode().value()).isEqualTo(400);
                });

        verify(repository, never()).getByCourtListId(any());
        verify(repository, never()).deleteById(any());
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

