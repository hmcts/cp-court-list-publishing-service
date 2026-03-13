package uk.gov.hmcts.cp.cleanup;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class RetentionPurgeJob implements Job {

    @Autowired
    private DataRetentionService dataRetentionService;

    @Override
    public void execute(JobExecutionContext context) {
        dataRetentionService.cleanupOldData();
    }
}
