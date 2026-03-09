package uk.gov.hmcts.cp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
@SuppressWarnings("HideUtilityClassConstructor")
public class Application {

    private static final String LOG_PATH_PROP = "LOG_PATH";
    private static final String DEFAULT_LOG_PATH = "./logs";

    public static void main(final String[] args) {
        ensureLogDirectoryExists();
        SpringApplication.run(Application.class, args);
    }

    /**
     * Creates the log directory if it does not exist, so Logback's file appender can write to it.
     * Uses the same LOG_PATH as logback.xml (system property, env var, or default ./logs).
     * Sets LOG_PATH as a system property if only the env var is set, so Logback resolves it.
     */
    private static void ensureLogDirectoryExists() {
        if (System.getProperty(LOG_PATH_PROP) == null && System.getenv(LOG_PATH_PROP) != null) {
            System.setProperty(LOG_PATH_PROP, System.getenv(LOG_PATH_PROP));
        }
        String logPath = System.getProperty(LOG_PATH_PROP, DEFAULT_LOG_PATH);
        Path dir = Paths.get(logPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            System.err.println("Could not create log directory: " + dir + " - " + e.getMessage());
        }
    }
}