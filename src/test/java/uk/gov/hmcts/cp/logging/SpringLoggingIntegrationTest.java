package uk.gov.hmcts.cp.logging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import uk.gov.hmcts.cp.NonTracingIntegrationTestSetup;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
class SpringLoggingIntegrationTest extends NonTracingIntegrationTestSetup {

    private final PrintStream originalStdOut = System.out;

    @AfterEach
    void afterEach() {
        System.setOut(originalStdOut);
    }

    @Test
    void springboot_test_should_log_correct_fields() throws IOException {
        MDC.put("any-mdc-field", "1234-1234");
        final ByteArrayOutputStream capturedStdOut = captureStdOut();
        log.info("spring boot test message");

        final Map<String, Object> capturedFields = new ObjectMapper().readValue(capturedStdOut.toString(StandardCharsets.UTF_8), new TypeReference<>() {
        });

        assertThat(capturedFields.get("any-mdc-field")).isEqualTo("1234-1234");
        assertThat(capturedFields.get("timestamp")).isNotNull();
        assertThat(capturedFields.get("logger_name")).isEqualTo("uk.gov.hmcts.cp.logging.SpringLoggingIntegrationTest");
        assertThat(capturedFields.get("thread_name")).isEqualTo("Test worker");
        assertThat(capturedFields.get("level")).isEqualTo("INFO");
        assertThat(capturedFields.get("message")).isEqualTo("spring boot test message");
    }

    private ByteArrayOutputStream captureStdOut() {
        final ByteArrayOutputStream capturedStdOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedStdOut, true, StandardCharsets.UTF_8));
        return capturedStdOut;
    }
}
