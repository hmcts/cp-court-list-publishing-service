package uk.gov.hmcts.cp.services.crown;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import uk.gov.hmcts.cp.task.CrownListPublishTask;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.service.ExecutionService;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CrownListTaskTriggerTest {

    @Mock
    private ExecutionService executionService;

    @InjectMocks
    private CrownListTaskTrigger trigger;

    @Test
    void queuesTheCrownPublishTaskWithTheRequestAsJobData() {
        CrownPublishRequest request = new CrownPublishRequest(UUID.randomUUID(), UUID.randomUUID(), CrownListType.WARNED,
                LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 12), "user-id", "001", null);

        trigger.queue(request);

        ArgumentCaptor<ExecutionInfo> execution = ArgumentCaptor.forClass(ExecutionInfo.class);
        verify(executionService).executeWith(execution.capture());
        assertThat(execution.getValue().getAssignedTaskName()).isEqualTo(CrownListPublishTask.TASK_NAME);
        assertThat(execution.getValue().isShouldRetry()).isFalse();
        assertThat(execution.getValue().getJobData()).isEqualTo(CrownListPublishTask.jobData(request));
    }

    @Test
    void carriesTheRequestsTraceContextIntoTheJobData() {
        CrownPublishRequest request = new CrownPublishRequest(UUID.randomUUID(), UUID.randomUUID(), CrownListType.DAILY,
                LocalDate.of(2026, 6, 9), LocalDate.of(2026, 6, 9), "user-id", "001", null);
        MDC.put("traceId", "trace-123");
        MDC.put("spanId", "span-456");
        try {
            trigger.queue(request);
        } finally {
            MDC.clear();
        }

        ArgumentCaptor<ExecutionInfo> execution = ArgumentCaptor.forClass(ExecutionInfo.class);
        verify(executionService).executeWith(execution.capture());
        assertThat(execution.getValue().getJobData().getString("traceId")).isEqualTo("trace-123");
        assertThat(execution.getValue().getJobData().getString("spanId")).isEqualTo("span-456");
    }
}
