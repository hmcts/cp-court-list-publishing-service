package uk.gov.hmcts.cp.task;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.services.CourtListStatusUpdater;
import uk.gov.hmcts.cp.services.crown.CrownListCaTHPublisher;
import uk.gov.hmcts.cp.services.crown.CrownListType;
import uk.gov.hmcts.cp.services.crown.CrownPublishRequest;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo.executionInfo;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus.COMPLETED;

@ExtendWith(MockitoExtension.class)
class CrownListPublishTaskTest {

    private static final UUID COURT_CENTRE_ID = UUID.fromString("cc000000-0000-0000-0000-000000000001");
    private static final LocalDate MONDAY = LocalDate.of(2026, 6, 8);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 6, 14);

    @Mock
    private CourtListStatusUpdater statusUpdater;
    @Mock
    private CrownListCaTHPublisher publisher;

    private UUID courtListId;
    private CrownListPublishTask task;

    @BeforeEach
    void setUp() {
        courtListId = UUID.randomUUID();
        task = new CrownListPublishTask(statusUpdater, publisher, true);
    }

    @Test
    void jobDataCarriesTheWholeRequest() {
        CrownPublishRequest request = request(CrownListType.FIRM, MONDAY, SUNDAY);

        task.execute(execution(CrownListPublishTask.jobData(request)));

        verify(publisher).publish(request);
    }

    @Test
    void publishesAndMarksThePublishSuccessful() {
        ExecutionInfo result = task.execute(execution(CrownListPublishTask.jobData(request(CrownListType.WARNED, MONDAY, SUNDAY))));

        assertThat(result.getExecutionStatus()).isEqualTo(COMPLETED);
        verify(statusUpdater).markPublishSuccessful(courtListId);
        verify(statusUpdater, never()).markFileFailed(any(), any());
    }

    @Test
    void recordsAFailureNamingTheListAndItsDates() {
        IllegalStateException failure = new IllegalStateException("CaTH returned status 500");
        doThrow(failure).when(publisher).publish(any());

        ExecutionInfo result = task.execute(execution(CrownListPublishTask.jobData(request(CrownListType.DAILY, MONDAY, MONDAY))));

        assertThat(result.getExecutionStatus()).isEqualTo(COMPLETED);
        ArgumentCaptor<Exception> recorded = ArgumentCaptor.forClass(Exception.class);
        verify(statusUpdater).markPublishFailed(eq(courtListId), recorded.capture());
        assertThat(recorded.getValue())
                .hasMessage("FINAL list 2026-06-08 to 2026-06-08 not published to CaTH: CaTH returned status 500")
                .hasCause(failure);
        verify(statusUpdater, never()).markPublishSuccessful(any());
    }

    @Test
    void recordsAFailureWhenTheJobBreaksTheListRules() {
        JsonObject jobData = Json.createObjectBuilder(CrownListPublishTask.jobData(request(CrownListType.DAILY, MONDAY, MONDAY)))
                .add(CrownListPublishTask.END_DATE, "2026-06-09")
                .build();

        task.execute(execution(jobData));

        verify(statusUpdater).markPublishFailed(eq(courtListId), any(IllegalArgumentException.class));
        verifyNoInteractions(publisher);
    }

    @Test
    void recordsPublishingDisabledAsAFailureWithItsReason() {
        CrownListPublishTask disabled = new CrownListPublishTask(statusUpdater, publisher, false);

        disabled.execute(execution(CrownListPublishTask.jobData(request(CrownListType.WARNED, MONDAY, SUNDAY))));

        verifyNoInteractions(publisher);
        ArgumentCaptor<Exception> reason = ArgumentCaptor.forClass(Exception.class);
        verify(statusUpdater).markPublishFailed(eq(courtListId), reason.capture());
        assertThat(reason.getValue()).hasMessage(CrownListPublishTask.PUBLISHING_DISABLED);
    }

    @Test
    void completesWithoutJobData() {
        ExecutionInfo result = task.execute(execution(null));

        assertThat(result.getExecutionStatus()).isEqualTo(COMPLETED);
        verifyNoInteractions(publisher, statusUpdater);
    }

    private CrownPublishRequest request(CrownListType type, LocalDate startDate, LocalDate endDate) {
        return new CrownPublishRequest(courtListId, COURT_CENTRE_ID, type, startDate, endDate, "user-id", "001", "0113 306 2800");
    }

    private static ExecutionInfo execution(JsonObject jobData) {
        return executionInfo().withJobData(jobData).withAssignedTaskName(CrownListPublishTask.TASK_NAME).build();
    }
}
