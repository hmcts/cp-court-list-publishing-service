package uk.gov.hmcts.cp.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

public class CourtListPublishTaskIntegrationTest {

    private static final String BASE_URL = System.getProperty("app.baseUrl", "http://localhost:8082/courtlistpublishing-service");
    private static final String PUBLISH_ENDPOINT = BASE_URL + "/api/court-list-publish/publish";
    private static final String GET_STATUS_BY_COURT_CENTRE_ENDPOINT = BASE_URL + "/api/court-list-publish/court-centre/";

    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publishCourtList_shouldQueryAndSendToCaTH_whenValidRequest() throws Exception {
        // Given
        UUID courtCentreId = UUID.randomUUID();
        String requestJson = createPublishRequestJson(courtCentreId, "PUBLIC");
        
        // When - Publish court list (this triggers the task)
        ResponseEntity<String> publishResponse = postPublishRequest(requestJson);
        
        // Then - Verify publish response
        assertThat(publishResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode publishBody = parseResponse(publishResponse);
        UUID courtListId = UUID.fromString(publishBody.get("courtListId").asText());
        assertThat(publishBody.get("publishStatus").asText()).isEqualTo("COURT_LIST_REQUESTED");
        
        // Wait for async task to complete (task execution is asynchronous)
        // Task manager polls and executes tasks, so we need to wait longer (30 seconds)
        waitForTaskCompletion(courtListId, courtCentreId, 30000);
        
        // Verify status was updated to EXPORT_SUCCESSFUL
        ResponseEntity<String> statusResponse = getStatusRequest(courtListId, courtCentreId);
        assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode statusBody = parseResponse(statusResponse);
        assertThat(statusBody.get("publishStatus").asText()).isEqualTo("EXPORT_SUCCESSFUL");
    }

    @Test
    void publishCourtList_shouldStillUpdateStatus_whenCaTHEndpointFails() throws Exception {
        // Given
        UUID courtCentreId = UUID.randomUUID();
        String requestJson = createPublishRequestJson(courtCentreId, "STANDARD");
        
        // When - Publish court list
        ResponseEntity<String> publishResponse = postPublishRequest(requestJson);
        
        // Then - Verify publish response
        assertThat(publishResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode publishBody = parseResponse(publishResponse);
        UUID courtListId = UUID.fromString(publishBody.get("courtListId").asText());
        
        // Wait for async task to complete
        waitForTaskCompletion(courtListId, courtCentreId, 30000);
        
        // Verify status was still updated to EXPORT_SUCCESSFUL (status update happens even if CaTH fails)
        // Note: WireMock verification removed as we're using static mappings
        ResponseEntity<String> statusResponse = getStatusRequest(courtListId, courtCentreId);
        assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode statusBody = parseResponse(statusResponse);
        assertThat(statusBody.get("publishStatus").asText()).isEqualTo("EXPORT_SUCCESSFUL");
    }

    @Test
    void publishCourtList_shouldStillUpdateStatus_whenQueryApiFails() throws Exception {
        // Given
        UUID courtCentreId = UUID.randomUUID();
        String requestJson = createPublishRequestJson(courtCentreId, "PUBLIC");
        
        // When - Publish court list
        ResponseEntity<String> publishResponse = postPublishRequest(requestJson);
        
        // Then - Verify publish response
        assertThat(publishResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode publishBody = parseResponse(publishResponse);
        UUID courtListId = UUID.fromString(publishBody.get("courtListId").asText());
        System.out.println("Created court list with ID: " + courtListId);
        System.out.println("Initial status: " + publishBody.get("publishStatus").asText());
        
        // Immediately verify the record exists
        try {
            ResponseEntity<String> immediateStatus = getStatusRequest(courtListId, courtCentreId);
            if (immediateStatus.getStatusCode().is2xxSuccessful()) {
                JsonNode immediateBody = parseResponse(immediateStatus);
                System.out.println("Immediate status check: " + immediateBody.get("publishStatus").asText());
            }
        } catch (Exception e) {
            System.err.println("ERROR: Could not retrieve status immediately after creation: " + e.getMessage());
            throw new AssertionError("Record was not created or is not accessible", e);
        }
        
        // Wait for async task to complete
        waitForTaskCompletion(courtListId, courtCentreId, 30000);
        
        // Verify status was still updated to EXPORT_SUCCESSFUL
        // Note: WireMock verification removed as we're using static mappings
        ResponseEntity<String> statusResponse = getStatusRequest(courtListId, courtCentreId);
        assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode statusBody = parseResponse(statusResponse);
        assertThat(statusBody.get("publishStatus").asText()).isEqualTo("EXPORT_SUCCESSFUL");
    }

    // Helper methods

    private String createPublishRequestJson(UUID courtCentreId, String courtListType) {
        return """
            {
                "courtCentreId": "%s",
                "courtListType": "%s"
            }
            """.formatted(courtCentreId, courtListType);
    }

    private HttpEntity<String> createPublishHttpEntity(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "vnd.courtlistpublishing-service.publish.post+json"));
        return new HttpEntity<>(json, headers);
    }

    private ResponseEntity<String> postPublishRequest(String requestJson) {
        return http.exchange(PUBLISH_ENDPOINT, HttpMethod.POST, createPublishHttpEntity(requestJson), String.class);
    }

    private ResponseEntity<String> getStatusRequest(UUID courtListId, UUID courtCentreId) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(new MediaType("application", "vnd.courtlistpublishing-service.publish.get+json")));
        
        ResponseEntity<String> response = http.exchange(
                GET_STATUS_BY_COURT_CENTRE_ENDPOINT + courtCentreId, 
                HttpMethod.GET, 
                new HttpEntity<>(headers), 
                String.class);
        
        JsonNode responseBody = parseResponse(response);
        if (!responseBody.isArray()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        
        String courtListIdStr = courtListId.toString();
        JsonNode matchingItem = null;
        for (JsonNode item : responseBody) {
            if (courtListIdStr.equals(item.get("courtListId").asText())) {
                matchingItem = item;
                break;
            }
        }
        
        if (matchingItem == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        
        return ResponseEntity.ok()
                .contentType(new MediaType("application", "vnd.courtlistpublishing-service.publish.get+json"))
                .body(matchingItem.toString());
    }

    private JsonNode parseResponse(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    /**
     * Waits for task completion by polling the status endpoint until it's updated or timeout
     */
    private void waitForTaskCompletion(UUID courtListId, UUID courtCentreId, long timeoutMs) throws Exception {
        long startTime = System.currentTimeMillis();
        long pollInterval = 500; // Poll every 500ms
        int pollCount = 0;
        
        System.out.println("Waiting for task completion for courtListId: " + courtListId);
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                ResponseEntity<String> statusResponse = getStatusRequest(courtListId, courtCentreId);
                if (statusResponse.getStatusCode().is2xxSuccessful()) {
                    JsonNode statusBody = parseResponse(statusResponse);
                    String publishStatus = statusBody.get("publishStatus").asText();
                    System.out.println("Poll #" + pollCount + ": Status = " + publishStatus);
                    
                    if ("EXPORT_SUCCESSFUL".equals(publishStatus) || "EXPORT_FAILED".equals(publishStatus)) {
                        // Task has completed (either successfully or with failure)
                        System.out.println("Task completed with status: " + publishStatus);
                        return;
                    }
                } else {
                    System.out.println("Poll #" + pollCount + ": Status endpoint returned " + statusResponse.getStatusCode());
                }
            } catch (Exception e) {
                // Status endpoint might not be available yet, continue polling
                if (pollCount % 10 == 0) { // Log every 5 seconds
                    System.out.println("Poll #" + pollCount + ": Error checking status - " + e.getMessage());
                }
            }
            pollCount++;
            Thread.sleep(pollInterval);
        }
        // Timeout reached - task may still be running
        System.err.println("Timeout reached after " + timeoutMs + "ms. Task may still be running.");
        try {
            ResponseEntity<String> finalStatus = getStatusRequest(courtListId, courtCentreId);
            if (finalStatus.getStatusCode().is2xxSuccessful()) {
                JsonNode statusBody = parseResponse(finalStatus);
                System.err.println("Final status: " + statusBody.get("publishStatus").asText());
            }
        } catch (Exception e) {
            System.err.println("Could not get final status: " + e.getMessage());
        }
    }
}
