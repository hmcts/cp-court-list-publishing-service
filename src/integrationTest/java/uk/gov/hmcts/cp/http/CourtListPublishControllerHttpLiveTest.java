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

    private static final String BASE_URL = System.getProperty("app.baseUrl", "http://localhost:8082/courtlistpublishing-service");
    private static final String API_ENDPOINT = BASE_URL + "/api/court-list-publish";
    private static final String PUBLISH_STATUS_PUBLISHED = "PUBLISHED";
    private static final String PUBLISH_STATUS_PENDING = "PENDING";
    private static final String COURT_LIST_TYPE_DAILY = "DAILY";

    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void postCourtListPublish_creates_court_list_publish_status_successfully() throws Exception {
        // Given
        UUID courtListId = UUID.randomUUID();
        UUID courtCentreId = UUID.randomUUID();
        String requestJson = createRequestJson(courtListId, courtCentreId, PUBLISH_STATUS_PUBLISHED, COURT_LIST_TYPE_DAILY);

        // When
        ResponseEntity<String> response = postRequest(requestJson);

        // Then
        assertSuccessfulCreation(response, courtListId, courtCentreId, PUBLISH_STATUS_PUBLISHED, COURT_LIST_TYPE_DAILY);
    }

    @Test
    void putCourtListPublish_updates_court_list_publish_status_successfully() throws Exception {
        // Given - First create a court list publish status
        UUID courtListId = UUID.randomUUID();
        UUID courtCentreId = UUID.randomUUID();
        postRequest(createRequestJson(courtListId, courtCentreId, PUBLISH_STATUS_PENDING, COURT_LIST_TYPE_DAILY));

        // When - Update the entity
        String updateRequestJson = createRequestJson(courtListId, courtCentreId, PUBLISH_STATUS_PUBLISHED, COURT_LIST_TYPE_DAILY);
        ResponseEntity<String> response = putRequest(updateRequestJson);

        // Then
        assertSuccessfulUpdate(response, courtListId, courtCentreId, PUBLISH_STATUS_PUBLISHED, COURT_LIST_TYPE_DAILY);
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

        // When & Then
        assertBadRequest(() -> postRequest(requestJson));
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

        // When & Then
        assertBadRequest(() -> postRequest(requestJson));
    }

    @Test
    void postCourtListPublish_creates_and_updates_same_entity() throws Exception {
        // Given
        UUID courtListId = UUID.randomUUID();
        UUID courtCentreId = UUID.randomUUID();

        // When - Create first time
        ResponseEntity<String> createResponse = postRequest(
                createRequestJson(courtListId, courtCentreId, PUBLISH_STATUS_PENDING, COURT_LIST_TYPE_DAILY)
        );

        // Then - Verify creation
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode createBody = parseResponse(createResponse);
        assertThat(createBody.get("publishStatus").asText()).isEqualTo(PUBLISH_STATUS_PENDING);

        // When - Update with same ID (upsert)
        ResponseEntity<String> updateResponse = postRequest(
                createRequestJson(courtListId, courtCentreId, PUBLISH_STATUS_PUBLISHED, COURT_LIST_TYPE_DAILY)
        );

        // Then - Verify update
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode updateBody = parseResponse(updateResponse);
        assertThat(updateBody.get("courtListId").asText()).isEqualTo(courtListId.toString());
        assertThat(updateBody.get("publishStatus").asText()).isEqualTo(PUBLISH_STATUS_PUBLISHED);
    }

    // Helper methods

    private String createRequestJson(UUID courtListId, UUID courtCentreId, String publishStatus, String courtListType) {
        return """
            {
                "courtListId": "%s",
                "courtCentreId": "%s",
                "publishStatus": "%s",
                "courtListType": "%s"
            }
            """.formatted(courtListId, courtCentreId, publishStatus, courtListType);
    }

    private HttpEntity<String> createHttpEntity(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(json, headers);
    }

    private ResponseEntity<String> postRequest(String requestJson) {
        return http.exchange(API_ENDPOINT, HttpMethod.POST, createHttpEntity(requestJson), String.class);
    }

    private ResponseEntity<String> putRequest(String requestJson) {
        return http.exchange(API_ENDPOINT, HttpMethod.PUT, createHttpEntity(requestJson), String.class);
    }

    private JsonNode parseResponse(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private void assertSuccessfulCreation(ResponseEntity<String> response, UUID courtListId, UUID courtCentreId,
                                          String publishStatus, String courtListType) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getBody()).isNotNull();

        JsonNode responseBody = parseResponse(response);
        assertThat(responseBody.get("courtListId").asText()).isEqualTo(courtListId.toString());
        assertThat(responseBody.get("courtCentreId").asText()).isEqualTo(courtCentreId.toString());
        assertThat(responseBody.get("publishStatus").asText()).isEqualTo(publishStatus);
        assertThat(responseBody.get("courtListType").asText()).isEqualTo(courtListType);
        assertThat(responseBody.get("lastUpdated")).isNotNull();
    }

    private void assertSuccessfulUpdate(ResponseEntity<String> response, UUID courtListId, UUID courtCentreId,
                                       String publishStatus, String courtListType) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getBody()).isNotNull();

        JsonNode responseBody = parseResponse(response);
        assertThat(responseBody.get("courtListId").asText()).isEqualTo(courtListId.toString());
        assertThat(responseBody.get("courtCentreId").asText()).isEqualTo(courtCentreId.toString());
        assertThat(responseBody.get("publishStatus").asText()).isEqualTo(publishStatus);
        assertThat(responseBody.get("courtListType").asText()).isEqualTo(courtListType);
        assertThat(responseBody.get("lastUpdated")).isNotNull();
    }

    private void assertBadRequest(Runnable request) {
        assertThatThrownBy(request::run)
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(exception -> {
                    HttpClientErrorException ex = (HttpClientErrorException) exception;
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }
}

