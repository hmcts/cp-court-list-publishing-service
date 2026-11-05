package uk.gov.hmcts.cp.services.crown;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.cp.filters.tracing.TracingFilter;
import uk.gov.hmcts.cp.task.CrownListPublishTask;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus;
import uk.gov.hmcts.cp.taskmanager.service.ExecutionService;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo.executionInfo;

/**
 * Queues {@link CrownListPublishTask} for a crown list publish. The request's trace context ({@code traceId},
 * {@code spanId} from {@link TracingFilter}) goes into the job data, so the task's log lines carry it too.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CrownListTaskTrigger {

    private final ExecutionService executionService;

    @Transactional
    public void queue(CrownPublishRequest request) {
        JsonObjectBuilder jobData = Json.createObjectBuilder(CrownListPublishTask.jobData(request));
        for (String key : CrownListPublishTask.TRACE_KEYS) {
            String value = MDC.get(key);
            if (value != null && !value.isBlank()) {
                jobData.add(key, value);
            }
        }
        executionService.executeWith(executionInfo()
                .withJobData(jobData.build())
                .withAssignedTaskName(CrownListPublishTask.TASK_NAME)
                .withAssignedTaskStartTime(ZonedDateTime.now(ZoneOffset.UTC))
                .withExecutionStatus(ExecutionStatus.STARTED)
                .withShouldRetry(false)
                .build());
        log.info("Crown list publish task queued, {}, courtListId={}", request.describe(), request.courtListId());
    }
}
