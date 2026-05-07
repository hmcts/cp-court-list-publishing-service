package uk.gov.hmcts.cp.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import uk.gov.hmcts.cp.controllers.GlobalExceptionHandler;

import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@TestPropertySource(properties = {
        "spring.application.name=cp-court-list-publishing-service",
        "jwt.filter.enabled=false",
        "spring.main.lazy-initialization=true",
        "server.servlet.context-path="
})
@WebMvcTest(controllers = TracingProbeController.class,
        excludeFilters = {@ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {GlobalExceptionHandler.class})}
)
@ActiveProfiles("test")
@Import(TestTracingConfig.class)
@Slf4j
class TracingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${spring.application.name}")
    private String springApplicationName;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger rootLogger;

    @BeforeEach
    void attachAppender() {
        rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        logAppender = new ListAppender<>();
        logAppender.start();
        rootLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachAppender() {
        if (rootLogger != null && logAppender != null) {
            rootLogger.detachAppender(logAppender);
            logAppender.stop();
        }
        MDC.clear();
    }

    @Test
    void incoming_request_should_add_new_tracing() throws Exception {
        mockMvc.perform(get("/_trace-probe")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().exists("traceId"));

        ILoggingEvent event = lastProbeEvent();
        Map<String, String> mdc = event.getMDCPropertyMap();
        assertThat(mdc.get("traceId")).as("traceId").isNotNull();
        assertThat(mdc.get("spanId")).as("spanId").isNotNull();

        // logger name is the FQCN; assert on the stable tail
        assertThat(event.getLoggerName())
                .as("logger name")
                .matches("(^|.*\\.)RootController$");

        assertThat(event.getFormattedMessage()).isEqualTo("START");
    }

    @Test
    void incoming_request_with_traceId_should_pass_through() throws Exception {
        var result = mockMvc.perform(
                get("/_trace-probe")
                        .header("traceId", "1234-1234")
                        .header("spanId", "567-567")
                        .accept(MediaType.APPLICATION_JSON)
        ).andExpect(status().isOk()).andReturn();

        ILoggingEvent event = lastProbeEvent();
        Map<String, String> mdc = event.getMDCPropertyMap();
        assertThat(mdc.get("traceId")).isEqualTo("1234-1234");
        assertThat(mdc.get("spanId")).isEqualTo("567-567");
        assertThat(mdc.get("applicationName")).isEqualTo(springApplicationName);

        assertThat(result.getResponse().getHeader("traceId")).isEqualTo("1234-1234");
        assertThat(result.getResponse().getHeader("spanId")).isEqualTo("567-567");
    }

    private ILoggingEvent lastProbeEvent() {
        List<ILoggingEvent> events = logAppender.list;
        for (int i = events.size() - 1; i >= 0; i--) {
            ILoggingEvent e = events.get(i);
            if ("START".equals(e.getFormattedMessage())
                    && e.getLoggerName().endsWith("RootController")) {
                return e;
            }
        }
        throw new IllegalStateException("No probe log event found; events=" + events);
    }
}
