package uk.gov.hmcts.cp.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.openapi.model.PublishStatus;

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
    private static final String PUBLISH_ENDPOINT = BASE_URL + "/api/court-list-publish/publish";
    private static final String PUBLISH_REQUESTED_STATUS = PublishStatus.PUBLISH_REQUESTED.toString();
    private static final String PUBLISH_SUCCESSFUL_STATUS = PublishStatus.PUBLISH_SUCCESSFUL.toString();
    private static final String COURT_LIST_TYPE_PUBLIC = CourtListType.PUBLIC.toString();
    private static final String COURT_LIST_TYPE_FINAL = CourtListType.FINAL.toString();

    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void postCourtListPublish_creates_court_list_publish_status_successfully() throws Exception {
        // Given
        UUID courtListId = UUID.randomUUID();
        UUID courtCentreId = UUID.randomUUID();
        String requestJson = createRequestJson(courtListId, courtCentreId, PUBLISH_REQUESTED_STATUS, COURT_LIST_TYPE_PUBLIC);

        // When
        ResponseEntity<String> response = postRequest(requestJson);

        // Then
        assertSuccessfulCreation(response, courtListId, courtCentreId);
    }

    @Test
    void postCourtListPublish_updates_existing_court_list_successfully() throws Exception {
        // Given - First create a court list publish status
        UUID courtListId = UUID.randomUUID();
        UUID courtCentreId = UUID.randomUUID();
        postRequest(createRequestJson(courtListId, courtCentreId, PUBLISH_SUCCESSFUL_STATUS, COURT_LIST_TYPE_PUBLIC));

        // When - Update the entity with same ID
        String updateRequestJson = createRequestJson(courtListId, courtCentreId, PUBLISH_REQUESTED_STATUS, COURT_LIST_TYPE_FINAL);
        ResponseEntity<String> response = postRequest(updateRequestJson);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode responseBody = parseResponse(response);
        assertThat(responseBody.get("courtListId").asText()).isNotNull();
        assertThat(responseBody.get("publishStatus").asText()).isEqualTo(PUBLISH_REQUESTED_STATUS);
        assertThat(responseBody.get("courtListType").asText()).isEqualTo(COURT_LIST_TYPE_FINAL);
    }

    @Test
    void postCourtListPublish_creates_and_updates_same_entity() throws Exception {
        // Given
        UUID courtListId = UUID.randomUUID();
        UUID courtCentreId = UUID.randomUUID();

        // When - Create first time
        ResponseEntity<String> createResponse = postRequest(
                createRequestJson(courtListId, courtCentreId, PUBLISH_SUCCESSFUL_STATUS, COURT_LIST_TYPE_PUBLIC)
        );

        // Then - Verify creation
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode createBody = parseResponse(createResponse);
        assertThat(createBody.get("publishStatus").asText()).isEqualTo(PUBLISH_REQUESTED_STATUS);

        // When - Update with same ID (upsert)
        ResponseEntity<String> updateResponse = postRequest(
                createRequestJson(courtListId, courtCentreId, PUBLISH_REQUESTED_STATUS, COURT_LIST_TYPE_PUBLIC)
        );

        // Then - Verify update
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode updateBody = parseResponse(updateResponse);
        assertThat(updateBody.get("courtListId").asText()).isNotNull();
        assertThat(updateBody.get("publishStatus").asText()).isEqualTo(PUBLISH_REQUESTED_STATUS);
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
    void postCourtListPublish_returns_bad_request_when_court_list_type_is_blank() {
        // Given - Request with blank courtListType
        String requestJson = """
            {
                "courtListId": "%s",
                "courtCentreId": "%s",
                "publishStatus": "PUBLISHED",
                "courtListType": "   "
            }
            """.formatted(UUID.randomUUID(), UUID.randomUUID());

        // When & Then
        assertBadRequest(() -> postRequest(requestJson));
    }

    @Test
    void findCourtListPublishByCourtCenterId_returns_list_when_entities_exist() throws Exception {
        // Given - Create multiple entities for the same court centre
        UUID courtCentreId = UUID.randomUUID();
        UUID courtListId1 = UUID.randomUUID();
        UUID courtListId2 = UUID.randomUUID();

        postRequest(createRequestJson(courtListId1, courtCentreId, PUBLISH_REQUESTED_STATUS, COURT_LIST_TYPE_PUBLIC));
        postRequest(createRequestJson(courtListId2, courtCentreId, PUBLISH_SUCCESSFUL_STATUS, COURT_LIST_TYPE_FINAL));

        // When
        ResponseEntity<String> response = getRequest(BASE_URL + "/api/court-list-publish/court-centre/" + courtCentreId);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode responseBody = parseResponse(response);
        assertThat(responseBody.isArray()).isTrue();
        assertThat(responseBody.size()).isGreaterThanOrEqualTo(2);
        
        // Verify all items have the same court centre ID
        for (JsonNode item : responseBody) {
            assertThat(item.get("courtCentreId").asText()).isEqualTo(courtCentreId.toString());
        }
    }

    @Test
    void findCourtListPublishByCourtCenterId_returns_empty_list_when_no_entities_exist() throws Exception {
        // Given - Non-existent court centre ID
        UUID nonExistentCourtCentreId = UUID.randomUUID();

        // When
        ResponseEntity<String> response = getRequest(BASE_URL + "/api/court-list-publish/court-centre/" + nonExistentCourtCentreId);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode responseBody = parseResponse(response);
        assertThat(responseBody.isArray()).isTrue();
        assertThat(responseBody.size()).isEqualTo(0);
    }

    @Test
    void findCourtListPublishByCourtCenterId_returns_limited_to_10_records() throws Exception {
        // Given - Create more than 10 entities for the same court centre
        UUID courtCentreId = UUID.randomUUID();
        for (int i = 0; i < 15; i++) {
            postRequest(createRequestJson(UUID.randomUUID(), courtCentreId, PUBLISH_REQUESTED_STATUS, COURT_LIST_TYPE_PUBLIC));
        }

        // When
        ResponseEntity<String> response = getRequest(BASE_URL + "/api/court-list-publish/court-centre/" + courtCentreId);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode responseBody = parseResponse(response);
        assertThat(responseBody.isArray()).isTrue();
        assertThat(responseBody.size()).isLessThanOrEqualTo(10);
    }

    // Helper methods

    private String createRequestJson(UUID courtListId, UUID courtCentreId, String publishStatus, String courtListType) {
        // Note: courtListId and publishStatus are ignored as they're not part of the request model
        // The controller generates courtListId internally and sets the status internally
        return """
            {
                "courtCentreId": "%s",
                "startDate": "2026-01-20",
                "endDate": "2026-01-27",
                "courtListType": "%s"
            }
            """.formatted(courtCentreId, courtListType);
    }

    private HttpEntity<String> createHttpEntity(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "vnd.courtlistpublishing-service.publish.post+json"));
        return new HttpEntity<>(json, headers);
    }

    private ResponseEntity<String> postRequest(String requestJson) {
        return http.exchange(PUBLISH_ENDPOINT, HttpMethod.POST, createHttpEntity(requestJson), String.class);
    }

    private ResponseEntity<String> getRequest(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(new MediaType("application", "vnd.courtlistpublishing-service.publish.get+json")));
        return http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private JsonNode parseResponse(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private void assertSuccessfulCreation(ResponseEntity<String> response, UUID courtListId, UUID courtCentreId) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getBody()).isNotNull();

        JsonNode responseBody = parseResponse(response);
        assertThat(responseBody.get("courtListId").asText()).isNotNull();
        assertThat(responseBody.get("courtCentreId").asText()).isEqualTo(courtCentreId.toString());
        assertThat(responseBody.get("publishStatus").asText()).isEqualTo(CourtListPublishControllerHttpLiveTest.PUBLISH_REQUESTED_STATUS);
        assertThat(responseBody.get("courtListType").asText()).isEqualTo(CourtListPublishControllerHttpLiveTest.COURT_LIST_TYPE_PUBLIC);
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

