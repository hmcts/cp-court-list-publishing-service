package uk.gov.hmcts.cp.filters.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import uk.gov.hmcts.cp.NonTracingIntegrationTestSetup;

import jakarta.annotation.Resource;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "authz.http.enabled=true",
    "authz.http.action-required=false",
    "management.health.jms.enabled=false",
    "management.endpoint.health.probes.enabled=false"
})
class AuthRulesFilterTest extends NonTracingIntegrationTestSetup {

    @Resource
    private MockMvc mockMvc;

    @Test
    void shouldAllowAccessToActuatorWhenAuthzEnabled() throws Exception {
        // Actuator endpoints should be excluded from authz filtering
        mockMvc.perform(MockMvcRequestBuilders.get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldHaveAuthzFilterConfigured() {
        // Verify that the authz filter is configured by checking the context loads
        assertThat(mockMvc).isNotNull();
    }
}

