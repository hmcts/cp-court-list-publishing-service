package uk.gov.hmcts.cp.services.crown;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.models.CourtCentreData;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishRequest;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishResponse;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.openapi.model.Status;
import uk.gov.hmcts.cp.services.CourtListStatusUpdater;
import uk.gov.hmcts.cp.services.ReferenceDataService;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrownCourtListPublishServiceTest {

    private static final String USER_ID = "test-user-id";
    private static final UUID COURT_CENTRE_ID = UUID.fromString("cc000000-0000-0000-0000-000000000001");
    private static final LocalDate MONDAY = LocalDate.of(2026, 6, 8);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 6, 14);

    @Mock
    private CrownListStatusService statusService;
    @Mock
    private CourtListStatusUpdater statusUpdater;
    @Mock
    private CrownListTaskTrigger taskTrigger;
    @Mock
    private ReferenceDataService referenceDataService;

    @InjectMocks
    private CrownCourtListPublishService service;

    @ParameterizedTest
    @CsvSource({"FINAL, 2026-06-10, 2026-06-10", "FIRM, 2026-06-08, 2026-06-14", "WARN, 2026-06-08, 2026-06-12"})
    void recordsThePublishAndQueuesTheTask(CourtListType courtListType, LocalDate startDate, LocalDate endDate) {
        UUID courtListId = UUID.randomUUID();
        crownCourt();
        CourtListPublishResponse expected = response(courtListId, courtListType);
        when(statusService.requestPublish(COURT_CENTRE_ID, courtListType, startDate)).thenReturn(expected);

        assertThat(service.publish(request(courtListType, startDate, endDate), USER_ID)).isSameAs(expected);

        verify(taskTrigger).queue(new CrownPublishRequest(courtListId, COURT_CENTRE_ID,
                CrownListType.from(courtListType).orElseThrow(), startDate, endDate, USER_ID, "001", "0113 306 2800"));
        verifyNoInteractions(statusUpdater);
    }

    @Test
    void refusesWithBadRequestWhileTheSameListIsBeingPublished() {
        crownCourt();
        when(statusService.requestPublish(COURT_CENTRE_ID, CourtListType.FIRM, MONDAY))
                .thenThrow(new CrownPublishInProgressException("A FIRM list publish starting 2026-06-08 is already in progress", null));

        assertThatThrownBy(() -> service.publish(request(CourtListType.FIRM, MONDAY, SUNDAY), USER_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).contains("already in progress");
                });
        verifyNoInteractions(taskTrigger, statusUpdater);
    }

    @Test
    void recordsAFailureAndReturns500WhenTheTaskCannotBeQueued() {
        UUID courtListId = UUID.randomUUID();
        crownCourt();
        when(statusService.requestPublish(COURT_CENTRE_ID, CourtListType.FINAL, MONDAY)).thenReturn(response(courtListId, CourtListType.FINAL));
        IllegalStateException failure = new IllegalStateException("jobs table unavailable");
        doThrow(failure).when(taskTrigger).queue(any());

        assertStatus(() -> service.publish(request(CourtListType.FINAL, MONDAY, MONDAY), USER_ID), HttpStatus.INTERNAL_SERVER_ERROR);

        verify(statusUpdater).markPublishFailed(courtListId, failure);
    }

    @ParameterizedTest
    @EnumSource(value = CourtListType.class, names = {"FINAL", "FIRM", "WARN"}, mode = EnumSource.Mode.EXCLUDE)
    void rejectsListTypesThatAreNotCrownLists(CourtListType courtListType) {
        assertStatus(() -> service.publish(request(courtListType, MONDAY, MONDAY), USER_ID), HttpStatus.BAD_REQUEST);
        verifyNothingRecorded();
    }

    @ParameterizedTest
    @CsvSource({"FINAL, 2026-06-08, 2026-06-09", "FIRM, 2026-06-09, 2026-06-14", "FIRM, 2026-06-08, 2026-06-15", "WARN, 2026-06-14, 2026-06-08"})
    void rejectsInvalidDates(CourtListType courtListType, LocalDate startDate, LocalDate endDate) {
        assertStatus(() -> service.publish(request(courtListType, startDate, endDate), USER_ID), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(referenceDataService);
        verifyNothingRecorded();
    }

    @Test
    void rejectsAMissingCourtCentreId() {
        CourtListPublishRequest request = CourtListPublishRequest.builder().courtListType(CourtListType.FINAL)
                .startDate(MONDAY).endDate(MONDAY).build();

        assertStatus(() -> service.publish(request, USER_ID), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(referenceDataService);
        verifyNothingRecorded();
    }

    @Test
    void rejectsACourtCentreThatIsNotACrownCourt() {
        when(referenceDataService.getCourtCenterDataByCourtCentreId(COURT_CENTRE_ID.toString(), USER_ID))
                .thenReturn(Optional.of(CourtCentreData.builder().oucodeL1Code("B").courtIdNumeric("415").build()));

        assertStatus(() -> service.publish(request(CourtListType.FINAL, MONDAY, MONDAY), USER_ID), HttpStatus.BAD_REQUEST);
        verifyNothingRecorded();
    }

    @Test
    void rejectsACrownCourtWithoutANumericCourtId() {
        when(referenceDataService.getCourtCenterDataByCourtCentreId(COURT_CENTRE_ID.toString(), USER_ID))
                .thenReturn(Optional.of(CourtCentreData.builder().oucodeL1Code("C").build()));

        assertStatus(() -> service.publish(request(CourtListType.FINAL, MONDAY, MONDAY), USER_ID), HttpStatus.BAD_REQUEST);
        verifyNothingRecorded();
    }

    @Test
    void rejectsACourtCentreMissingFromReferenceData() {
        when(referenceDataService.getCourtCenterDataByCourtCentreId(COURT_CENTRE_ID.toString(), USER_ID)).thenReturn(Optional.empty());

        assertStatus(() -> service.publish(request(CourtListType.FINAL, MONDAY, MONDAY), USER_ID), HttpStatus.BAD_REQUEST);
        verifyNothingRecorded();
    }

    private static void assertStatus(ThrowingCallable call, HttpStatus expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(expected));
    }

    private void crownCourt() {
        when(referenceDataService.getCourtCenterDataByCourtCentreId(COURT_CENTRE_ID.toString(), USER_ID))
                .thenReturn(Optional.of(CourtCentreData.builder().oucodeL1Code("C").courtIdNumeric("001").phone("0113 306 2800").build()));
    }

    private void verifyNothingRecorded() {
        verifyNoInteractions(statusService, taskTrigger, statusUpdater);
    }

    private static CourtListPublishRequest request(CourtListType type, LocalDate startDate, LocalDate endDate) {
        return CourtListPublishRequest.builder().courtCentreId(COURT_CENTRE_ID).courtListType(type)
                .startDate(startDate).endDate(endDate).build();
    }

    private static CourtListPublishResponse response(UUID courtListId, CourtListType type) {
        return CourtListPublishResponse.builder().courtListId(courtListId).courtCentreId(COURT_CENTRE_ID)
                .courtListType(type).publishStatus(Status.REQUESTED).build();
    }
}
