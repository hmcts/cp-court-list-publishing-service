package uk.gov.hmcts.cp.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.service.task.ExecutableTask;
import uk.gov.hmcts.cp.taskmanager.service.task.Task;

import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo.executionInfo;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus.COMPLETED;


@Task("COURT_LIST_PUBLISH_TASK")
@Component
public class CourtListPublishTask implements ExecutableTask {

    private final Logger logger = LoggerFactory.getLogger(CourtListPublishTask.class);

    @Override
    public ExecutionInfo execute(ExecutionInfo executionInfo) {
        logger.info("COURT_LIST_PUBLISH_TASK [job {}]", executionInfo);

        return executionInfo().from(executionInfo)
                .withExecutionStatus(COMPLETED)
                .build();
    }
}
