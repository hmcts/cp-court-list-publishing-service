package uk.gov.hmcts.cp.testconfig;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

@TestConfiguration
@EnableAutoConfiguration(exclude = {
        org.springframework.boot.micrometer.tracing.autoconfigure.OpenTelemetryTracingAutoConfiguration.class
})
@Import(BaseTestConfiguration.class)
public class NonTracingIntegrationTestConfiguration {
}
