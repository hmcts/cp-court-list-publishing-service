package uk.gov.hmcts.cp.services.crown;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import uk.gov.hmcts.cp.domain.CourtListStatusEntity;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishResponse;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.openapi.model.Status;
import uk.gov.hmcts.cp.repositories.CourtListStatusRepository;
import uk.gov.hmcts.cp.services.CourtListPublishStatusService;
import uk.gov.hmcts.cp.services.CourtListStatusUpdater;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrownListStatusServiceTest {

    private static final UUID COURT_CENTRE_ID = UUID.randomUUID();
    private static final LocalDate MONDAY = LocalDate.of(2026, 6, 8);
    private static final Duration TIMEOUT = Duration.ofMinutes(15);

    @Mock
    private CourtListStatusRepository repository;
    @Mock
    private CourtListStatusUpdater statusUpdater;
    @Mock
    private CourtListPublishStatusService courtListPublishStatusService;

    private CrownListStatusService service;

    @BeforeEach
    void setUp() {
        service = new CrownListStatusService(repository, statusUpdater, courtListPublishStatusService, TIMEOUT);
    }

    @Test
    void createsARowWithPublishRequestedAndNoFileStatus() {
        when(repository.lockByCourtCentreIdAndPublishDateAndCourtListType(COURT_CENTRE_ID, MONDAY, CourtListType.FIRM))
                .thenReturn(Optional.empty());
        CourtListPublishResponse expected = new CourtListPublishResponse();
        when(courtListPublishStatusService.findPublishStatus(any(UUID.class), isNull(), isNull(), isNull()))
                .thenReturn(List.of(expected));

        CourtListPublishResponse response = service.requestPublish(COURT_CENTRE_ID, CourtListType.FIRM, MONDAY);

        ArgumentCaptor<CourtListStatusEntity> saved = ArgumentCaptor.forClass(CourtListStatusEntity.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPublishStatus()).isEqualTo(Status.REQUESTED);
        assertThat(saved.getValue().getFileStatus()).isNull();
        assertThat(saved.getValue().getPublishDate()).isEqualTo(MONDAY);
        verify(courtListPublishStatusService).findPublishStatus(saved.getValue().getCourtListId(), null, null, null);
        assertThat(response).isSameAs(expected);
    }

    @Test
    void aFinishedPublishIsRequestedAgainOnTheSameRow() {
        CourtListStatusEntity row = row(Status.SUCCESSFUL, Instant.now());
        when(repository.lockByCourtCentreIdAndPublishDateAndCourtListType(COURT_CENTRE_ID, MONDAY, CourtListType.FINAL))
                .thenReturn(Optional.of(row));
        when(courtListPublishStatusService.findPublishStatus(row.getCourtListId(), null, null, null))
                .thenReturn(List.of(new CourtListPublishResponse()));

        service.requestPublish(COURT_CENTRE_ID, CourtListType.FINAL, MONDAY);

        verify(statusUpdater).markPublishRequested(row.getCourtListId());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void refusesWhileAPublishOfTheSameListIsInProgress() {
        when(repository.lockByCourtCentreIdAndPublishDateAndCourtListType(COURT_CENTRE_ID, MONDAY, CourtListType.WARN))
                .thenReturn(Optional.of(row(Status.REQUESTED, Instant.now().minusSeconds(30))));

        assertThatThrownBy(() -> service.requestPublish(COURT_CENTRE_ID, CourtListType.WARN, MONDAY))
                .isInstanceOf(CrownPublishInProgressException.class)
                .hasMessageContaining("already in progress");
        verify(statusUpdater, never()).markPublishRequested(any());
    }

    @Test
    void anAbandonedPublishDoesNotBlockTheList() {
        CourtListStatusEntity row = row(Status.REQUESTED, Instant.now().minus(TIMEOUT).minusSeconds(60));
        when(repository.lockByCourtCentreIdAndPublishDateAndCourtListType(COURT_CENTRE_ID, MONDAY, CourtListType.WARN))
                .thenReturn(Optional.of(row));
        when(courtListPublishStatusService.findPublishStatus(row.getCourtListId(), null, null, null))
                .thenReturn(List.of(new CourtListPublishResponse()));

        service.requestPublish(COURT_CENTRE_ID, CourtListType.WARN, MONDAY);

        verify(statusUpdater).markPublishRequested(row.getCourtListId());
    }

    @Test
    void aConcurrentCreateOfTheSameRowIsReportedAsInProgress() {
        when(repository.lockByCourtCentreIdAndPublishDateAndCourtListType(COURT_CENTRE_ID, MONDAY, CourtListType.FIRM))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(CourtListStatusEntity.class))).thenThrow(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"" + CrownListStatusService.UNIQUE_INDEX + "\""));

        assertThatThrownBy(() -> service.requestPublish(COURT_CENTRE_ID, CourtListType.FIRM, MONDAY))
                .isInstanceOf(CrownPublishInProgressException.class)
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anyOtherIntegrityViolationIsNotReportedAsInProgress() {
        when(repository.lockByCourtCentreIdAndPublishDateAndCourtListType(COURT_CENTRE_ID, MONDAY, CourtListType.FIRM))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(CourtListStatusEntity.class))).thenThrow(new DataIntegrityViolationException(
                "new row violates check constraint \"ck_file_status_required_for_non_sjp\""));

        assertThatThrownBy(() -> service.requestPublish(COURT_CENTRE_ID, CourtListType.FIRM, MONDAY))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static CourtListStatusEntity row(Status publishStatus, Instant lastUpdated) {
        CourtListStatusEntity row = new CourtListStatusEntity(UUID.randomUUID(), COURT_CENTRE_ID, publishStatus,
                null, CourtListType.FINAL, lastUpdated);
        row.setPublishDate(MONDAY);
        return row;
    }
}
