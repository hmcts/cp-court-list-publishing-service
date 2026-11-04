package uk.gov.hmcts.cp.services.crown;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.cp.task.CrownListPublishTask;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus;
import uk.gov.hmcts.cp.taskmanager.service.ExecutionService;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo.executionInfo;

/** Queues {@link CrownListPublishTask} for a crown list publish. */
@Service
@RequiredArgsConstructor
@Slf4j
public class CrownListTaskTrigger {

    private final ExecutionService executionService;

    @Transactional
    public void queue(CrownPublishRequest request) {
        executionService.executeWith(executionInfo()
                .withJobData(CrownListPublishTask.jobData(request))
                .withAssignedTaskName(CrownListPublishTask.TASK_NAME)
                .withAssignedTaskStartTime(ZonedDateTime.now(ZoneOffset.UTC))
                .withExecutionStatus(ExecutionStatus.STARTED)
                .withShouldRetry(false)
                .build());
        log.info("Crown list publish task queued, {}, courtListId={}", request.describe(), request.courtListId());
    }
}
