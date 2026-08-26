package uk.gov.hmcts.cp.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.domain.CourtListStatusEntity;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.openapi.model.Status;
import uk.gov.hmcts.cp.repositories.CourtListStatusRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Covers the status precedence shared by the publish and file status pairs: a new attempt always
 * resets the row to the transient REQUESTED, and within a cycle the status only moves up
 * (REQUESTED -> FAILED -> SUCCESSFUL). Jobs are assigned in batches to a thread pool and can
 * finish out of order, so a SUCCESSFUL still overtakes a FAILED, while a late FAILED is dropped.
 */
@ExtendWith(MockitoExtension.class)
class CourtListStatusUpdaterTest {

    @Mock
    private CourtListStatusRepository repository;

    private CourtListStatusUpdater updater;
    private UUID courtListId;

    @BeforeEach
    void setUp() {
        updater = new CourtListStatusUpdater(repository);
        courtListId = UUID.randomUUID();
    }

    private CourtListStatusEntity entityWith(Status publishStatus, Status fileStatus) {
        CourtListStatusEntity entity = new CourtListStatusEntity(
                courtListId, null, publishStatus, fileStatus,
                CourtListType.SJP_PUBLIC_FULL_ENGLISH, Instant.now());
        entity.setPublishDate(LocalDate.of(2025, 3, 9));
        when(repository.getByCourtListId(courtListId)).thenReturn(entity);
        return entity;
    }

    // ── a new attempt always reopens the cycle ───────────────────────────────

    @Test
    void markPublishRequested_resetsFailedRow_andClearsPreviousError() {
        CourtListStatusEntity entity = entityWith(Status.FAILED, null);
        entity.setPublishErrorMessage("previous stack trace");

        updater.markPublishRequested(courtListId);

        assertThat(entity.getPublishStatus()).isEqualTo(Status.REQUESTED);
        assertThat(entity.getPublishErrorMessage()).isNull();
        verify(repository).save(entity);
    }

    @Test
    void markPublishRequested_reopensCycle_evenWhenRowIsSuccessful() {
        CourtListStatusEntity entity = entityWith(Status.SUCCESSFUL, null);

        updater.markPublishRequested(courtListId);

        assertThat(entity.getPublishStatus()).isEqualTo(Status.REQUESTED);
    }

    // ── outcomes within a cycle ──────────────────────────────────────────────

    @Test
    void markPublishFailed_recordsFailure_whenAttemptIsTheCurrentOne() {
        CourtListStatusEntity entity = entityWith(Status.REQUESTED, null);

        updater.markPublishFailed(courtListId, new RuntimeException("CaTH publish failed with HTTP status 504"));

        assertThat(entity.getPublishStatus()).isEqualTo(Status.FAILED);
        assertThat(entity.getPublishErrorMessage()).contains("504");
        verify(repository).save(entity);
    }

    @Test
    void markPublishFailed_leavesRowUntouched_whenALaterAttemptAlreadySucceeded() {
        CourtListStatusEntity entity = entityWith(Status.SUCCESSFUL, null);
        Instant lastUpdated = entity.getLastUpdated();

        updater.markPublishFailed(courtListId, new RuntimeException("CaTH publish failed with HTTP status 504"));

        assertThat(entity.getPublishStatus()).isEqualTo(Status.SUCCESSFUL);
        assertThat(entity.getPublishErrorMessage()).isNull();
        assertThat(entity.getLastUpdated()).isEqualTo(lastUpdated);
        verify(repository, never()).save(any());
    }

    @Test
    void markPublishFailed_refreshesTheError_whenTheAttemptFailsAgain() {
        CourtListStatusEntity entity = entityWith(Status.FAILED, null);
        entity.setPublishErrorMessage("previous stack trace");

        updater.markPublishFailed(courtListId, new RuntimeException("CaTH publish failed with HTTP status 502"));

        assertThat(entity.getPublishStatus()).isEqualTo(Status.FAILED);
        assertThat(entity.getPublishErrorMessage()).contains("502").doesNotContain("previous stack trace");
    }

    @Test
    void markPublishSuccessful_overridesAFailureFromASupersededAttempt() {
        CourtListStatusEntity entity = entityWith(Status.FAILED, null);
        entity.setPublishErrorMessage("previous stack trace");

        updater.markPublishSuccessful(courtListId);

        assertThat(entity.getPublishStatus()).isEqualTo(Status.SUCCESSFUL);
        assertThat(entity.getPublishErrorMessage()).isNull();
    }

    // ── the same rule governs the file status pair ───────────────────────────

    @Test
    void markFileFailed_recordsFailure_whenAttemptIsTheCurrentOne() {
        CourtListStatusEntity entity = entityWith(Status.SUCCESSFUL, Status.REQUESTED);

        updater.markFileFailed(courtListId, new RuntimeException("PDF upload failed"));

        assertThat(entity.getFileStatus()).isEqualTo(Status.FAILED);
        assertThat(entity.getFileErrorMessage()).contains("PDF upload failed");
    }

    @Test
    void markFileFailed_recordsFailure_whenNoFileStatusHasBeenRecordedYet() {
        CourtListStatusEntity entity = entityWith(Status.SUCCESSFUL, null);

        updater.markFileFailed(courtListId, new RuntimeException("PDF upload failed"));

        assertThat(entity.getFileStatus()).isEqualTo(Status.FAILED);
        assertThat(entity.getFileErrorMessage()).contains("PDF upload failed");
    }
    @Test
    void markFileFailed_leavesRowUntouched_whenALaterAttemptAlreadyUploadedTheFile() {
        CourtListStatusEntity entity = entityWith(Status.SUCCESSFUL, Status.SUCCESSFUL);
        UUID fileId = UUID.randomUUID();
        entity.setFileId(fileId);

        updater.markFileFailed(courtListId, new RuntimeException("PDF upload failed"));

        assertThat(entity.getFileStatus()).isEqualTo(Status.SUCCESSFUL);
        assertThat(entity.getFileId()).isEqualTo(fileId);
        assertThat(entity.getFileErrorMessage()).isNull();
        verify(repository, never()).save(any());
    }

    @Test
    void markFileSuccessful_recordsFileIdAndBumpsPublishCount() {
        CourtListStatusEntity entity = entityWith(Status.SUCCESSFUL, Status.REQUESTED);
        entity.setFileErrorMessage("previous stack trace");
        UUID fileId = UUID.randomUUID();

        updater.markFileSuccessful(courtListId, fileId);

        assertThat(entity.getFileStatus()).isEqualTo(Status.SUCCESSFUL);
        assertThat(entity.getFileId()).isEqualTo(fileId);
        assertThat(entity.getFileErrorMessage()).isNull();
        assertThat(entity.getPublishCount()).isEqualTo(1);
    }

    // ── missing row ──────────────────────────────────────────────────────────

    @Test
    void markPublishSuccessful_doesNothing_whenNoRowExists() {
        when(repository.getByCourtListId(courtListId)).thenReturn(null);

        updater.markPublishSuccessful(courtListId);

        verify(repository, never()).save(any());
    }
}
