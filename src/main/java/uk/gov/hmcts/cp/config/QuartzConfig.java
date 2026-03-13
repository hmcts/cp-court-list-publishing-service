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
import uk.gov.hmcts.cp.cleanup.RetentionPurgeJob;
import uk.gov.hmcts.cp.cleanup.RetentionPurgeProperties;

@Configuration
@ConditionalOnProperty(name = "cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class QuartzConfig {

    private final Scheduler scheduler;
    private final RetentionPurgeProperties props;

    public QuartzConfig(Scheduler scheduler, RetentionPurgeProperties props) {
        this.scheduler = scheduler;
        this.props = props;
    }

    @PostConstruct
    public void scheduleRetentionPurgeJob() throws SchedulerException {
        JobDetail jobDetail = JobBuilder.newJob(RetentionPurgeJob.class)
                .withIdentity("retentionPurgeJob")
                .storeDurably()
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .forJob(jobDetail)
                .withIdentity("retentionPurgeTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule(props.getCron()))
                .build();

        scheduler.scheduleJob(jobDetail, trigger);
    }
}
