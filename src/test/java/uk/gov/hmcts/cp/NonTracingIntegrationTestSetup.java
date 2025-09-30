package uk.gov.hmcts.cp;

import uk.gov.hmcts.cp.testconfig.NonTracingIntegrationTestConfiguration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(NonTracingIntegrationTestConfiguration.class)
public class NonTracingIntegrationTestSetup {
    // Base class for integration tests that need to exclude tracing dependencies
}
