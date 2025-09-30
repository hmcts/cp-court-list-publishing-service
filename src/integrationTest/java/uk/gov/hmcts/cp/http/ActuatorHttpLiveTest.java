package uk.gov.hmcts.cp.http;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

public class ActuatorHttpLiveTest {

    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082");
    private final RestTemplate http = new RestTemplate();

    @Test
    void health_is_up() {
        final ResponseEntity<String> res = http.exchange(
                baseUrl + "/actuator/health", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                String.class
        );
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void prometheus_is_exposed() {
        final HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setAccept(java.util.List.of(MediaType.TEXT_PLAIN));
        final ResponseEntity<String> res = http.exchange(
                baseUrl + "/actuator/prometheus", HttpMethod.GET,
                new HttpEntity<>(httpHeaders),
                String.class
        );
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("application_started_time_seconds");
    }
}
