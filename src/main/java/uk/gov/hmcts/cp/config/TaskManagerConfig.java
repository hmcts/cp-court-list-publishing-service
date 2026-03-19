package uk.gov.hmcts.cp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration for Task Manager Service.
 * 
 * Enables Spring's scheduling functionality which is required for the JobExecutor
 * to poll the database for unassigned jobs and execute them.
 */
@Configuration
@EnableScheduling
public class TaskManagerConfig {
    // This configuration enables @Scheduled annotation support
    // which is required by the task-manager-service's JobExecutor
}
