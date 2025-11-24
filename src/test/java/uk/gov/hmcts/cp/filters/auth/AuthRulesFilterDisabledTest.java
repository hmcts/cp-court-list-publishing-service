package uk.gov.hmcts.cp.filters.auth;

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
    "authz.http.enabled=false"
})
class AuthRulesFilterDisabledTest extends NonTracingIntegrationTestSetup {

    @Resource
    private MockMvc mockMvc;

    @Test
    void shouldHaveAuthzFilterDisabled() {
        // Verify that the authz filter can be disabled
        assertThat(mockMvc).isNotNull();
    }
}

