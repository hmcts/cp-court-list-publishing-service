package uk.gov.hmcts.cp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Config for cleanup/schedule-job: executor for running cleanup once when the schedule-job API is called.
 */
@Configuration
public class CleanupConfig {

    @Bean(name = "cleanupJobExecutor")
    public Executor cleanupJobExecutor() {
        return new SimpleAsyncTaskExecutor("cleanup-job-");
    }
}
