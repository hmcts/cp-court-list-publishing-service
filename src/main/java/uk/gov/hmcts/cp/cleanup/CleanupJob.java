package uk.gov.hmcts.cp.cleanup;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

/**
 * Quartz job that runs retention cleanup at the scheduled cron.
 * retentionDays is passed via JobDataMap when the job is scheduled.
 */
@Setter
@Component
@Slf4j
public class CleanupJob extends QuartzJobBean {

    private CleanupJobService cleanupJobService;

    private int retentionDays;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        if (cleanupJobService == null) {
            log.warn("RetentionCleanupJob: CleanupJobService not injected, skipping");
            return;
        }
        log.info("RetentionCleanupJob running: retentionDays={}", retentionDays);
        cleanupJobService.cleanupOldData(retentionDays);
    }
}
