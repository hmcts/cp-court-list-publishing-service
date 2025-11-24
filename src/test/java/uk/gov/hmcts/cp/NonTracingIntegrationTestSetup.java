package uk.gov.hmcts.cp;

import uk.gov.hmcts.cp.testconfig.NonTracingIntegrationTestConfiguration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@Import(NonTracingIntegrationTestConfiguration.class)
@TestPropertySource(properties = {
    "audit.http.enabled=false",
    "audit.http.openapi-rest-spec=test-openapi-spec.yml",
    "cp.audit.enabled=false"
})
public class NonTracingIntegrationTestSetup {
    // Base class for integration tests that need to exclude tracing dependencies
}
