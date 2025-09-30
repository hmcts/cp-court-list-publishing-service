package uk.gov.hmcts.cp.filters.jwt;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.hmcts.cp.NonTracingIntegrationTestSetup;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.cp.filters.jwt.JWTFilter.JWT_TOKEN_HEADER;

@SpringBootTest(properties = {"jwt.filter.enabled=true"})
@AutoConfigureMockMvc
class JWTFilterIntegrationTest extends NonTracingIntegrationTestSetup {

    @Resource
    private MockMvc mockMvc;

    @Resource
    private JWTService jwtService;

    @Test
    @SuppressWarnings("PMD.UnitTestShouldIncludeAssert")
    void shouldPassWhenTokenIsValid() throws Exception {
        final String jwtToken = jwtService.createToken();
        mockMvc
                .perform(
                        MockMvcRequestBuilders.get("/")
                                .header(JWT_TOKEN_HEADER, jwtToken)
                ).andExpectAll(
                        status().isOk(),
                        content().string("Welcome to service-hmcts-springboot-template, " + JWTService.USER)
                );
    }

    @Test
    void shouldFailWhenTokenIsMissing() {
        assertThatExceptionOfType(HttpClientErrorException.class)
                .isThrownBy(() -> mockMvc
                        .perform(
                                MockMvcRequestBuilders.get("/")
                        ))
                .withMessageContaining("No jwt token passed");
    }
}