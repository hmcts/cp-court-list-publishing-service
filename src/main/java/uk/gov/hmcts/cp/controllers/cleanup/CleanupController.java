package uk.gov.hmcts.cp.controllers.cleanup;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.cleanup.CleanupJob;
import uk.gov.hmcts.cp.cleanup.CleanupJobService;
import uk.gov.hmcts.cp.filters.jwt.AuthDetails;

import java.util.concurrent.Executor;

/** POST /schedule-job: system user only. Schedules cleanup at given cron and runs once immediately. */
@RestController
@Slf4j
public class CleanupController {

    private static final String MEDIA_TYPE = "application/vnd.courtlistpublishing-service.schedule-job+json";
    private static final MediaType SCHEDULE_JOB_MEDIA_TYPE = MediaType.parseMediaType(MEDIA_TYPE);
    private static final int DEFAULT_RETENTION_DAYS = 90;
    private static final String JOB_GROUP = "cleanup";
    private static final String JOB_NAME = "retention-cleanup";
    private static final String TRIGGER_NAME = "retention-cleanup-trigger";

    @Value("${courtlistpublishing.system-user-id:}")
    private String systemUserId;

    @Resource
    private AuthDetails authDetails;

    private final ObjectProvider<CleanupJobService> cleanupJobServices;
    private final ObjectProvider<Scheduler> schedulerProvider;
    private final Executor cleanupJobExecutor;

    public CleanupController(
            ObjectProvider<CleanupJobService> cleanupJobServices,
            ObjectProvider<Scheduler> schedulerProvider,
            @Qualifier("cleanupJobExecutor") Executor cleanupJobExecutor) {
        this.cleanupJobServices = cleanupJobServices;
        this.schedulerProvider = schedulerProvider;
        this.cleanupJobExecutor = cleanupJobExecutor;
    }

    @RequestMapping(
            method = RequestMethod.POST,
            value = "/api/court-list-publish/schedule-job",
            produces = {MEDIA_TYPE, "application/json"},
            consumes = MEDIA_TYPE
    )
    public ResponseEntity<Void> scheduleJob(@RequestBody(required = false) CleanupJobRequest request) {
        if (!isSystemUser()) {
            log.warn("Schedule job rejected: caller is not system user");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only system user may schedule jobs");
        }
        validateCronExpression(request);

        int retentionDays = request.getRetentionDays() != null ? request.getRetentionDays() : DEFAULT_RETENTION_DAYS;
        String cronExpression = request.getCronExpression();

        CleanupJobService service = cleanupJobServices.getIfAvailable();
        if (service == null) {
            log.warn("Schedule job rejected: cleanup service not available");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Cleanup service not available");
        }
        Scheduler scheduler = schedulerProvider.getIfAvailable();
        if (scheduler == null) {
            log.warn("Schedule job rejected: Quartz Scheduler not available");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Scheduler not available");
        }

        cleanupJobExecutor.execute(() -> {
            try {
                service.cleanupOldData(retentionDays);
            } catch (Exception e) {
                log.error("Schedule-job immediate cleanup run failed", e);
            }
        });

        try {
            JobKey jobKey = new JobKey(JOB_NAME, JOB_GROUP);
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
            }
            JobDetail jobDetail = JobBuilder.newJob(CleanupJob.class)
                    .withIdentity(jobKey)
                    .usingJobData("retentionDays", retentionDays)
                    .storeDurably(false)
                    .build();
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(TRIGGER_NAME, JOB_GROUP)
                    .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                    .forJob(jobDetail)
                    .build();
            scheduler.scheduleJob(jobDetail, trigger);
            log.info("Scheduled retention cleanup job: cron={}, retentionDays={}", cronExpression, retentionDays);
        } catch (SchedulerException e) {
            log.error("Failed to schedule retention cleanup job", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to schedule job", e);
        }

        return ResponseEntity.accepted().contentType(SCHEDULE_JOB_MEDIA_TYPE).build();
    }

    private boolean isSystemUser() {
        if (systemUserId == null || systemUserId.isBlank()) {
            return false;
        }
        return systemUserId.equals(authDetails.getUserId());
    }

    private void validateCronExpression(CleanupJobRequest request) {
        if (request == null || request.getCronExpression() == null || request.getCronExpression().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request must include cronExpression");
        }
        try {
            CronExpression.parse(request.getCronExpression());
        } catch (IllegalArgumentException e) {
            log.warn("Schedule job rejected: invalid cronExpression '{}'", request.getCronExpression(), e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cronExpression: " + e.getMessage());
        }
    }
}
