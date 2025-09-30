package uk.gov.hmcts.cp.logging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import uk.gov.hmcts.cp.testconfig.TracingIntegrationTestConfiguration;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Slf4j
@EnableAutoConfiguration(exclude = {
        org.springframework.boot.micrometer.tracing.autoconfigure.OpenTelemetryTracingAutoConfiguration.class
})
@Import(TracingIntegrationTestConfiguration.class)
@TestPropertySource(properties = {
        "spring.application.name=service-hmcts-springboot-template"
})
class TracingIntegrationTest {

    // Constants for tracing field names
    private static final String TRACE_ID_FIELD = "traceId";
    private static final String SPAN_ID_FIELD = "spanId";

    // Constants for test trace values
    private static final String TEST_TRACE_ID_1 = "test-trace-id-12345";
    private static final String TEST_SPAN_ID_1 = "test-span-id-67890";
    private static final String TEST_TRACE_ID_2 = "1234-1234";
    private static final String TEST_SPAN_ID_2 = "567-567";

    @Value("${spring.application.name}")
    private String springApplicationName;

    @Resource
    private MockMvc mockMvc;

    private final PrintStream originalStdOut = System.out;

    @BeforeEach
    public void setUp() {
        // Manually populate MDC with trace information similar to TracingFilter
        MDC.put(TRACE_ID_FIELD, TEST_TRACE_ID_1);
        MDC.put(SPAN_ID_FIELD, TEST_SPAN_ID_1);
        MDC.put("applicationName", springApplicationName);
    }

    @AfterEach
    public void afterEach() {
        System.setOut(originalStdOut);
        // Clear MDC after each test
        MDC.clear();
    }

    @Test
    void incoming_request_should_add_new_tracing() throws Exception {
        final MvcResultHelper result = performRequestAndCaptureLogs("/", null, null);
        final Map<String, Object> rootControllerLog = findRootControllerLog(result.capturedLogOutput());

        assertTracingFields(rootControllerLog, TEST_TRACE_ID_1, TEST_SPAN_ID_1);
        assertCommonLogFields(rootControllerLog);
    }

    @Test
    void incoming_request_with_traceId_should_pass_through() throws Exception {
        // Override the MDC with the header values that would be set by TracingFilter
        MDC.put(TRACE_ID_FIELD, TEST_TRACE_ID_2);
        MDC.put(SPAN_ID_FIELD, TEST_SPAN_ID_2);

        final MvcResultHelper result = performRequestAndCaptureLogs("/", TEST_TRACE_ID_2, TEST_SPAN_ID_2);
        final Map<String, Object> rootControllerLog = findRootControllerLog(result.capturedLogOutput());

        assertTracingFields(rootControllerLog, TEST_TRACE_ID_2, TEST_SPAN_ID_2);
        assertResponseHeaders(result.mvcResult(), TEST_TRACE_ID_2, TEST_SPAN_ID_2);
    }

    private ByteArrayOutputStream captureStdOut() {
        final ByteArrayOutputStream capturedStdOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedStdOut, true, StandardCharsets.UTF_8));
        return capturedStdOut;
    }

    private MvcResultHelper performRequestAndCaptureLogs(final String path, final String traceId, final String spanId) throws Exception {
        final ByteArrayOutputStream capturedStdOut = captureStdOut();

        MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders.get(path);
        if (traceId != null) {
            requestBuilder = requestBuilder.header(TRACE_ID_FIELD, traceId);
        }
        if (spanId != null) {
            requestBuilder = requestBuilder.header(SPAN_ID_FIELD, spanId);
        }

        final MvcResult result = mockMvc.perform(requestBuilder)
                .andExpect(status().isOk())
                .andReturn();

        return new MvcResultHelper(result, capturedStdOut.toString(StandardCharsets.UTF_8));
    }

    private Map<String, Object> findRootControllerLog(final String logOutput) throws Exception {
        final String[] logLines = logOutput.split("\n");
        final ObjectMapper objectMapper = new ObjectMapper();
        final TypeReference<Map<String, Object>> typeReference = new TypeReference<>() {};

        Map<String, Object> stringObjectMap = null;
        for (final String logLine : logLines) {
            if (logLine.contains("START") && logLine.contains("RootController") && stringObjectMap == null) {
                stringObjectMap = objectMapper.readValue(logLine, typeReference);
            }
        }

        if (stringObjectMap != null) {
            return stringObjectMap;
        } else {
            throw new AssertionError("RootController log message not found in output: " + logOutput);
        }
    }

    private void assertTracingFields(final Map<String, Object> log, final String expectedTraceId, final String expectedSpanId) {
        assertThat(log).isNotNull();
        assertThat(log.get(TRACE_ID_FIELD)).isEqualTo(expectedTraceId);
        assertThat(log.get(SPAN_ID_FIELD)).isEqualTo(expectedSpanId);
        assertThat(log.get("applicationName")).isEqualTo(springApplicationName);
    }

    private void assertCommonLogFields(final Map<String, Object> log) {
        assertThat(log.get("logger_name")).isEqualTo("uk.gov.hmcts.cp.controllers.RootController");
        assertThat(log.get("message")).isEqualTo("START");
    }

    private void assertResponseHeaders(final MvcResult result, final String expectedTraceId, final String expectedSpanId) {
        assertThat(result.getResponse().getHeader(TRACE_ID_FIELD)).isEqualTo(expectedTraceId);
        assertThat(result.getResponse().getHeader(SPAN_ID_FIELD)).isEqualTo(expectedSpanId);
    }

    // Helper class to encapsulate MvcResult and captured log output
    private record MvcResultHelper(MvcResult mvcResult, String capturedLogOutput) {
    }
}
