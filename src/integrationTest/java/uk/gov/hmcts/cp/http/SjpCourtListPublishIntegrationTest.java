package uk.gov.hmcts.cp.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;

/**
 * Integration tests for the SJP (Single Justice Procedure) court list publish endpoint.
 * POST /api/court-list-publish/sjp/publishCourtList. The API model currently only exposes listType;
 * listPayload is not passed through, so the service returns FAILED when listPayload would be required.
 */
public class SjpCourtListPublishIntegrationTest extends AbstractTest {

    private static final String BASE_URL = System.getProperty("app.baseUrl", "http://localhost:8082/courtlistpublishing-service");
    private static final String SJP_PUBLISH_ENDPOINT = BASE_URL + "/api/court-list-publish/sjp/publishCourtList";
    private static final MediaType SJP_CONTENT_TYPE =
            new MediaType("application", "vnd.courtlistpublishing-service.sjp.post+json");

    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper objectMapper = ObjectMapperConfig.getObjectMapper();

    @Test
    void postSjpCourtList_returnsFailed_whenListPayloadInBodyButNotInApiModel() throws Exception {
        // API model only has listType; listPayload in JSON is not passed to service, so service returns FAILED
        String requestJson = """
            {
                "listType": "SJP_PUBLISH_LIST",
                "language": "ENGLISH",
                "requestType": "FULL",
                "listPayload": {
                    "generatedDateAndTime": "2024-01-01T12:00:00Z",
                    "readyCases": [
                        {
                            "caseUrn": "SJP-CASE-001",
                            "defendantName": "Defendant One",
                            "sjpOffences": [{"title": "Offence title", "wording": "Offence wording"}]
                        }
                    ]
                }
            }
            """;

        ResponseEntity<String> response = postSjpRequest(requestJson);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("status").asText()).isEqualTo("FAILED");
        assertThat(body.get("listType").asText()).isEqualTo("SJP_PUBLISH_LIST");
        assertThat(body.get("message").asText()).contains("listPayload is required");
    }

    @Test
    void postSjpCourtList_returnsFailed_whenListPayloadWithEmptyReadyCases() throws Exception {
        // listPayload not passed through (API model has only listType), so service returns FAILED
        String requestJson = """
            {
                "listType": "SJP_PUBLISH_LIST",
                "listPayload": {
                    "generatedDateAndTime": "2024-01-01T12:00:00Z",
                    "readyCases": []
                }
            }
            """;

        ResponseEntity<String> response = postSjpRequest(requestJson);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("status").asText()).isEqualTo("FAILED");
        assertThat(body.get("message").asText()).contains("listPayload is required");
    }

    @Test
    void postSjpCourtList_returnsFailed_whenListPayloadMissing() throws Exception {
        String requestJson = """
            {
                "listType": "SJP_PUBLISH_LIST",
                "language": "ENGLISH"
            }
            """;

        ResponseEntity<String> response = postSjpRequest(requestJson);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("status").asText()).isEqualTo("FAILED");
        assertThat(body.get("message").asText()).contains("listPayload is required");
    }

    @Test
    void postSjpCourtList_returnsBadRequest_whenListTypeMissing() {
        String requestJson = """
            {
                "listPayload": {
                    "generatedDateAndTime": "2024-01-01T12:00:00Z",
                    "readyCases": []
                }
            }
            """;

        assertThatThrownBy(() -> postSjpRequest(requestJson))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> assertThat(((HttpClientErrorException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void postSjpCourtList_returnsBadRequest_whenListTypeInvalid() {
        String requestJson = """
            {
                "listType": "INVALID_TYPE",
                "listPayload": {
                    "generatedDateAndTime": "2024-01-01T12:00:00Z",
                    "readyCases": []
                }
            }
            """;

        assertThatThrownBy(() -> postSjpRequest(requestJson))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> assertThat(((HttpClientErrorException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void postSjpCourtList_returnsFailed_forSjpPressListWithListPayload() throws Exception {
        // listPayload not passed through (API model has only listType), so service returns FAILED
        String requestJson = """
            {
                "listType": "SJP_PRESS_LIST",
                "language": "ENGLISH",
                "listPayload": {
                    "generatedDateAndTime": "2024-01-01T12:00:00Z",
                    "readyCases": [
                        {
                            "caseUrn": "SJP-PRESS-001",
                            "defendantName": "Defendant",
                            "sjpOffences": [{"title": "Offence"}]
                        }
                    ]
                }
            }
            """;

        ResponseEntity<String> response = postSjpRequest(requestJson);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("status").asText()).isEqualTo("FAILED");
        assertThat(body.get("listType").asText()).isEqualTo("SJP_PRESS_LIST");
        assertThat(body.get("message").asText()).contains("listPayload is required");
    }

    private HttpEntity<String> createSjpHttpEntity(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(SJP_CONTENT_TYPE);
        return new HttpEntity<>(json, headers);
    }

    private ResponseEntity<String> postSjpRequest(String requestJson) {
        return http.exchange(SJP_PUBLISH_ENDPOINT, HttpMethod.POST, createSjpHttpEntity(requestJson), String.class);
    }
}
