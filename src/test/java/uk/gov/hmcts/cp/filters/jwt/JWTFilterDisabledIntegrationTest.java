package uk.gov.hmcts.cp.filters.jwt;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import uk.gov.hmcts.cp.NonTracingIntegrationTestSetup;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JWTFilterDisabledIntegrationTest extends NonTracingIntegrationTestSetup {

    @Resource
    private MockMvc mockMvc;

    @DisplayName("JWT filter should not complain of missing JWT when the filter is disabled")
    @Test
    @SuppressWarnings("PMD.UnitTestShouldIncludeAssert")
    void shouldNotFailWhenTokenIsMissingAndFilterIsDisabled() throws Exception {
        mockMvc
                .perform(
                        MockMvcRequestBuilders.get("/")
                ).andExpectAll(
                        status().isOk(),
                        content().string(containsString("Welcome to service-hmcts-springboot-template"))
                );
    }
}