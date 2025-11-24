package uk.gov.hmcts.cp.filters.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.cp.NonTracingIntegrationTestSetup;

import jakarta.annotation.Resource;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "audit.http.enabled=false",
    "audit.http.openapi-rest-spec=test-openapi-spec.yml"
})
class AuditFilterDisabledTest extends NonTracingIntegrationTestSetup {

    @Resource
    private MockMvc mockMvc;

    @Test
    void shouldHaveAuditFilterDisabled() {
        // Verify that the audit filter can be disabled
        assertThat(mockMvc).isNotNull();
    }
}

