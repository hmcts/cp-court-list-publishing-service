package uk.gov.hmcts.cp.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CourtListPublishControllerHttpLiveTest {

    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082/courtlistpublishing-service");
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void postCourtListPublish_creates_court_list_publish_status_successfully() throws Exception {
        // Given
        UUID courtListId = UUID.randomUUID();
        UUID courtCentreId = UUID.randomUUID();
        String publishStatus = "PUBLISHED";
        String courtListType = "DAILY";

        String requestJson = """
            {
                "courtListId": "%s",
                "courtCentreId": "%s",
                "publishStatus": "%s",
                "courtListType": "%s"
            }
            """.formatted(courtListId, courtCentreId, publishStatus, courtListType);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // When
        final ResponseEntity<String> response = http.exchange(
                baseUrl + "/api/court-list-publish",
                HttpMethod.POST,
                new HttpEntity<>(requestJson, headers),
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getBody()).isNotNull();

        JsonNode responseBody = objectMapper.readTree(response.getBody());
        assertThat(responseBody.get("courtListId").asText()).isEqualTo(courtListId.toString());
        assertThat(responseBody.get("courtCentreId").asText()).isEqualTo(courtCentreId.toString());
        assertThat(responseBody.get("publishStatus").asText()).isEqualTo(publishStatus);
        assertThat(responseBody.get("courtListType").asText()).isEqualTo(courtListType);
        assertThat(responseBody.get("lastUpdated")).isNotNull();
    }

    @Test
    void putCourtListPublish_updates_court_list_publish_status_successfully() throws Exception {
        // Given - First create a court list publish status
        UUID courtListId = UUID.randomUUID();
        UUID courtCentreId = UUID.randomUUID();
        String initialStatus = "PENDING";
        String updatedStatus = "PUBLISHED";
        String courtListType = "DAILY";

        String createRequestJson = """
            {
                "courtListId": "%s",
                "courtCentreId": "%s",
                "publishStatus": "%s",
                "courtListType": "%s"
            }
            """.formatted(courtListId, courtCentreId, initialStatus, courtListType);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Create the entity first
        http.exchange(
                baseUrl + "/api/court-list-publish",
                HttpMethod.POST,
                new HttpEntity<>(createRequestJson, headers),
                String.class
        );

        // Update request
        String updateRequestJson = """
            {
                "courtListId": "%s",
                "courtCentreId": "%s",
                "publishStatus": "%s",
                "courtListType": "%s"
            }
            """.formatted(courtListId, courtCentreId, updatedStatus, courtListType);

        // When - Update the entity
        final ResponseEntity<String> response = http.exchange(
                baseUrl + "/api/court-list-publish",
                HttpMethod.PUT,
                new HttpEntity<>(updateRequestJson, headers),
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getBody()).isNotNull();

        JsonNode responseBody = objectMapper.readTree(response.getBody());
        assertThat(responseBody.get("courtListId").asText()).isEqualTo(courtListId.toString());
        assertThat(responseBody.get("courtCentreId").asText()).isEqualTo(courtCentreId.toString());
        assertThat(responseBody.get("publishStatus").asText()).isEqualTo(updatedStatus);
        assertThat(responseBody.get("courtListType").asText()).isEqualTo(courtListType);
        assertThat(responseBody.get("lastUpdated")).isNotNull();
    }

    @Test
    void postCourtListPublish_returns_bad_request_when_missing_required_fields() {
        // Given - Request with missing courtListId
        String requestJson = """
            {
                "courtCentreId": "%s",
                "publishStatus": "PUBLISHED",
                "courtListType": "DAILY"
            }
            """.formatted(UUID.randomUUID());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // When & Then
        assertThatThrownBy(() -> http.exchange(
                baseUrl + "/api/court-list-publish",
                HttpMethod.POST,
                new HttpEntity<>(requestJson, headers),
                String.class
        ))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(exception -> {
                    HttpClientErrorException ex = (HttpClientErrorException) exception;
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    void postCourtListPublish_returns_bad_request_when_publish_status_is_blank() {
        // Given - Request with blank publishStatus
        String requestJson = """
            {
                "courtListId": "%s",
                "courtCentreId": "%s",
                "publishStatus": "   ",
                "courtListType": "DAILY"
            }
            """.formatted(UUID.randomUUID(), UUID.randomUUID());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // When & Then
        assertThatThrownBy(() -> http.exchange(
                baseUrl + "/api/court-list-publish",
                HttpMethod.POST,
                new HttpEntity<>(requestJson, headers),
                String.class
        ))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(exception -> {
                    HttpClientErrorException ex = (HttpClientErrorException) exception;
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    void postCourtListPublish_creates_and_updates_same_entity() throws Exception {
        // Given
        UUID courtListId = UUID.randomUUID();
        UUID courtCentreId = UUID.randomUUID();
        String initialStatus = "PENDING";
        String updatedStatus = "PUBLISHED";
        String courtListType = "DAILY";

        String requestJson = """
            {
                "courtListId": "%s",
                "courtCentreId": "%s",
                "publishStatus": "%s",
                "courtListType": "%s"
            }
            """.formatted(courtListId, courtCentreId, initialStatus, courtListType);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // When - Create first time
        final ResponseEntity<String> createResponse = http.exchange(
                baseUrl + "/api/court-list-publish",
                HttpMethod.POST,
                new HttpEntity<>(requestJson, headers),
                String.class
        );

        // Then - Verify creation
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode createBody = objectMapper.readTree(createResponse.getBody());
        assertThat(createBody.get("publishStatus").asText()).isEqualTo(initialStatus);

        // When - Update with same ID (upsert)
        String updateRequestJson = """
            {
                "courtListId": "%s",
                "courtCentreId": "%s",
                "publishStatus": "%s",
                "courtListType": "%s"
            }
            """.formatted(courtListId, courtCentreId, updatedStatus, courtListType);

        final ResponseEntity<String> updateResponse = http.exchange(
                baseUrl + "/api/court-list-publish",
                HttpMethod.POST,
                new HttpEntity<>(updateRequestJson, headers),
                String.class
        );

        // Then - Verify update
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode updateBody = objectMapper.readTree(updateResponse.getBody());
        assertThat(updateBody.get("courtListId").asText()).isEqualTo(courtListId.toString());
        assertThat(updateBody.get("publishStatus").asText()).isEqualTo(updatedStatus);
    }
}

