package uk.gov.hmcts.cp.config;

import jakarta.annotation.PostConstruct;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import uk.gov.hmcts.cp.cleanup.CleanupJob;
import uk.gov.hmcts.cp.cleanup.CleanupProperties;

@Configuration
@ConditionalOnProperty(name = "cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class QuartzConfig {

    private static final String JOB_NAME = "retentionCleanupJob";
    private static final String TRIGGER_NAME = "retentionCleanupTrigger";

    private final Scheduler scheduler;
    private final CleanupProperties props;

    public QuartzConfig(Scheduler scheduler, CleanupProperties props) {
        this.scheduler = scheduler;
        this.props = props;
    }

    @PostConstruct
    public void scheduleRetentionCleanupJob() throws SchedulerException {
        JobDetail jobDetail = JobBuilder.newJob(CleanupJob.class)
                .withIdentity(JOB_NAME)
                .usingJobData("retentionDays", props.getRetentionDays())
                .storeDurably(false)
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(TRIGGER_NAME)
                .forJob(jobDetail)
                .withSchedule(CronScheduleBuilder.cronSchedule(props.getCron()))
                .build();

        scheduler.scheduleJob(jobDetail, trigger);
    }
}
