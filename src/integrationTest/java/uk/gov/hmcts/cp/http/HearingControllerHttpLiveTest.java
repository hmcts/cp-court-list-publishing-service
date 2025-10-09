package uk.gov.hmcts.cp.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

public class HearingControllerHttpLiveTest {

    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082");
    private final RestTemplate http = new RestTemplate();

    @Test
    void getHearing_returns_hearing_data_when_valid_id() {
        // Given a valid hearing ID (you may need to create test data first)
        UUID hearingId = UUID.randomUUID();
        String randomPayload = "Test Data " + UUID.randomUUID();

        String hearingRequestJson = """
            {
                "hearingId": "%s",
                "payload": "%s"                
            }
            """.formatted(hearingId, randomPayload);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // When
        final ResponseEntity<String> postRes = http.exchange(
                baseUrl + "/api/hearing",
                HttpMethod.POST,
                new HttpEntity<>(hearingRequestJson, headers),
                String.class
        );

        assertThat(postRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(postRes.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(postRes.getBody()).isNotNull();
        assertThat(postRes.getBody()).contains(randomPayload);


        // When
        final ResponseEntity<String> getRes = http.exchange(
                baseUrl + "/api/hearing/" + hearingId,
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                String.class
        );

        // Then
        assertThat(getRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getRes.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(getRes.getBody()).isEqualTo(randomPayload);
    }


    @Test
    void postHearing_creates_hearing_successfully() {
        // Given a valid hearing request with random payload
        String randomPayload = "Test Data " + UUID.randomUUID();

        String hearingRequestJson = """
            {
                "hearingId": "%s",
                "payload": "%s"                
            }
            """.formatted(UUID.randomUUID(), randomPayload);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // When
        final ResponseEntity<String> res = http.exchange(
                baseUrl + "/api/hearing",
                HttpMethod.POST,
                new HttpEntity<>(hearingRequestJson, headers),
                String.class
        );

        // Then
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody()).contains(randomPayload);
    }
}