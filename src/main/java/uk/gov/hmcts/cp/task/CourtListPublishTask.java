package uk.gov.hmcts.cp.task;

import com.taskmanager.domain.ExecutionInfo;
import com.taskmanager.service.task.ExecutableTask;
import com.taskmanager.service.task.Task;
import jakarta.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static com.taskmanager.domain.ExecutionInfo.executionInfo;
import static com.taskmanager.domain.ExecutionStatus.COMPLETED;

@Task("COURT_LIST_PUBLISH_TASK")
@Component
public class CourtListPublishTask implements ExecutableTask {

    private final Logger logger = LoggerFactory.getLogger(CourtListPublishTask.class);

    @Override
    public ExecutionInfo execute(ExecutionInfo executionInfo) {
        logger.info("COURT_LIST_PUBLISH_TASK [job {}]", executionInfo);
        JsonObject jobData = executionInfo.getJobData();

        if (jobData == null) {
            logger.error("Job data is null for execution: {}", executionInfo);
            throw new IllegalArgumentException("Job data cannot be null");
        }
        return executionInfo().from(executionInfo)
                .withExecutionStatus(COMPLETED)
                .build();
    }
}

