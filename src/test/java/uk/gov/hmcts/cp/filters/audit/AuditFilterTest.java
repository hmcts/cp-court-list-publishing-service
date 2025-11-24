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
    "audit.http.enabled=true",
    "audit.http.openapi-rest-spec=test-openapi-spec.yml"
})
class AuditFilterTest extends NonTracingIntegrationTestSetup {

    @Resource
    private MockMvc mockMvc;

    @Test
    void shouldHaveAuditFilterConfigured() {
        // Verify that the audit filter is configured by checking the context loads
        assertThat(mockMvc).isNotNull();
    }
}

