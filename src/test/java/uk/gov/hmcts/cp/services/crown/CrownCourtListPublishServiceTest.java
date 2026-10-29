package uk.gov.hmcts.cp.services.crown;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.domain.CourtListStatusEntity;
import uk.gov.hmcts.cp.domain.PublishSource;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishResponse;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.openapi.model.Status;
import uk.gov.hmcts.cp.repositories.CourtListStatusRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrownCourtListPublishServiceTest {

    private static final UUID COURT_CENTRE_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final LocalDate START = LocalDate.parse("2026-11-02");
    private static final LocalDate END = LocalDate.parse("2026-11-08");
    private static final String USER_ID = "5b773ce6-7573-4979-8746-203778160d1e";

    @Mock
    private CourtListStatusRepository repository;

    @Mock
    private CrownTaskTriggerService taskTriggerService;

    private CrownCourtListPublishService service;

    @BeforeEach
    void setUp() {
        service = new CrownCourtListPublishService(repository, taskTriggerService);
    }

    @Test
    void createsARequestedRowAndQueuesTheTask() {
        when(repository.findByCourtCentreIdAndPublishDateAndCourtListTypeAndPublishSource(COURT_CENTRE_ID, START, CourtListType.WARN, PublishSource.CROWN))
                .thenReturn(Optional.empty());
        when(repository.save(any(CourtListStatusEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final CourtListPublishResponse response = service.publish(COURT_CENTRE_ID, CourtListType.WARN, START, END, USER_ID);

        final ArgumentCaptor<CourtListStatusEntity> saved = ArgumentCaptor.forClass(CourtListStatusEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getCourtCentreId()).isEqualTo(COURT_CENTRE_ID);
        assertThat(saved.getValue().getCourtListType()).isEqualTo(CourtListType.WARN);
        assertThat(saved.getValue().getPublishDate()).isEqualTo(START);
        assertThat(saved.getValue().getPublishStatus()).isEqualTo(Status.REQUESTED);
        assertThat(saved.getValue().getFileStatus()).as("there is no PDF, so no file status").isNull();
        assertThat(saved.getValue().getPublishSource()).isEqualTo(PublishSource.CROWN);

        assertThat(response.getCourtListId()).isEqualTo(saved.getValue().getCourtListId());
        assertThat(response.getPublishStatus()).isEqualTo(Status.REQUESTED);
        verify(taskTriggerService).triggerCrownPublishTask(response.getCourtListId(), COURT_CENTRE_ID, CourtListType.WARN, START, END, USER_ID);
    }

    @Test
    void reusesTheRowForTheSameCentreTypeAndStartDateAndClearsThePreviousError() {
        final CourtListStatusEntity existing = new CourtListStatusEntity(
                UUID.randomUUID(), COURT_CENTRE_ID, Status.FAILED, null, CourtListType.FINAL, Instant.parse("2026-10-01T00:00:00Z"));
        existing.setPublishDate(START);
        existing.setPublishErrorMessage("CaTH rejected the list");
        when(repository.findByCourtCentreIdAndPublishDateAndCourtListTypeAndPublishSource(COURT_CENTRE_ID, START, CourtListType.FINAL, PublishSource.CROWN))
                .thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        final CourtListPublishResponse response = service.publish(COURT_CENTRE_ID, CourtListType.FINAL, START, START, USER_ID);

        assertThat(response.getCourtListId()).isEqualTo(existing.getCourtListId());
        assertThat(existing.getPublishStatus()).isEqualTo(Status.REQUESTED);
        assertThat(existing.getPublishErrorMessage()).isNull();
        assertThat(existing.getLastUpdated()).isAfter(Instant.parse("2026-10-01T00:00:00Z"));
        verify(taskTriggerService).triggerCrownPublishTask(existing.getCourtListId(), COURT_CENTRE_ID, CourtListType.FINAL, START, START, USER_ID);
    }

    @Test
    void acceptsEveryListTypeThatMapsToACrownSchema() {
        when(repository.findByCourtCentreIdAndPublishDateAndCourtListTypeAndPublishSource(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(repository.save(any(CourtListStatusEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        for (final CourtListType type : new CourtListType[] {CourtListType.FINAL}) {
            assertThat(service.publish(COURT_CENTRE_ID, type, START, START, USER_ID).getCourtListType()).isEqualTo(type);
        }
        for (final CourtListType type : new CourtListType[] {CourtListType.WARN, CourtListType.FIRM}) {
            assertThat(service.publish(COURT_CENTRE_ID, type, START, END, USER_ID).getCourtListType()).isEqualTo(type);
        }
    }

    @Test
    void rejectsACourtListTypeThatHasNoCrownSchema() {
        assertBadRequest(() -> service.publish(COURT_CENTRE_ID, CourtListType.STANDARD, START, END, USER_ID), "cannot be published as a Crown list");
        assertBadRequest(() -> service.publish(COURT_CENTRE_ID, CourtListType.ONLINE_PUBLIC, START, END, USER_ID), "cannot be published as a Crown list");
        assertBadRequest(() -> service.publish(COURT_CENTRE_ID, CourtListType.DRAFT, START, START, USER_ID), "use FINAL");
        verifyNoInteractions(taskTriggerService);
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsMissingOrInconsistentInput() {
        assertBadRequest(() -> service.publish(null, CourtListType.FINAL, START, END, USER_ID), "Court centre ID is required");
        assertBadRequest(() -> service.publish(COURT_CENTRE_ID, null, START, END, USER_ID), "Court list type is required");
        assertBadRequest(() -> service.publish(COURT_CENTRE_ID, CourtListType.FINAL, null, END, USER_ID), "Start date and end date are required");
        assertBadRequest(() -> service.publish(COURT_CENTRE_ID, CourtListType.FINAL, START, null, USER_ID), "Start date and end date are required");
        assertBadRequest(() -> service.publish(COURT_CENTRE_ID, CourtListType.FINAL, END, START, USER_ID), "End date must not be before start date");
        assertBadRequest(() -> service.publish(COURT_CENTRE_ID, CourtListType.FINAL, START, END, USER_ID), "must be the same for a daily list");
        verifyNoInteractions(taskTriggerService);
        verify(repository, never()).save(any());
    }

    private static void assertBadRequest(final Runnable action, final String message) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining(message);
    }
}
