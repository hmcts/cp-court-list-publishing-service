package uk.gov.hmcts.cp.http;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * Base class for integration test classes that use WireMock.
 * Resets WireMock stub mappings before each test class runs so tests start from a clean state
 * (reloads mappings from the static files under wiremock/mappings).
 * <p>
 * WireMock runs in Docker; from the host the admin API is on port 8089 (see docker-compose).
 */
public abstract class AbstractTest {

    private static final String WIREMOCK_BASE_URL =
            System.getProperty("wiremock.baseUrl", "http://localhost:8089");
    private static final String RESET_MAPPINGS = WIREMOCK_BASE_URL + "/__admin/mappings/reset";

    @BeforeAll
    static void initTest() {
        resetWireMock();
    }

    static void resetWireMock() {
        RestTemplate rest = new RestTemplate();
        ResponseEntity<String> response = rest.exchange(
                RESET_MAPPINGS,
                HttpMethod.POST,
                new HttpEntity<>(new HttpHeaders()),
                String.class
        );
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException(
                    "WireMock reset failed: " + response.getStatusCode() + " from " + RESET_MAPPINGS);
        }
    }


}
