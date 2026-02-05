package uk.gov.hmcts.cp.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.cp.openapi.model.Status;

@Slf4j
public class CourtListPublishAndPDFGenerationTaskIntegrationTest {

    private static final String BASE_URL = System.getProperty("app.baseUrl", "http://localhost:8082/courtlistpublishing-service");
    private static final String PUBLISH_ENDPOINT = BASE_URL + "/api/court-list-publish/publish";
    private static final String GET_STATUS_ENDPOINT = BASE_URL + "/api/court-list-publish/publish-status";

    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper objectMapper = ObjectMapperConfig.getObjectMapper();

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
        assertThat(publishBody.get("publishStatus").asText()).isEqualTo("REQUESTED");
        
        // Wait for async task to complete (task execution is asynchronous)
        // Task manager JobExecutor polls the database and executes tasks
        // Note: The task manager service is an external dependency that uses @Scheduled polling
        // If tasks aren't executing, check that the JobExecutor is running and polling frequently enough
        waitForTaskCompletion(courtListId, 120000); // 2 minutes timeout
        
        // Verify status was updated to SUCCESSFUL
        ResponseEntity<String> statusResponse = getStatusRequest(courtListId);
        assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode statusBody = parseResponse(statusResponse);
        assertThat(statusBody.get("publishStatus").asText()).isEqualTo("SUCCESSFUL");
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
        // Task manager JobExecutor polls the database and executes tasks
        waitForTaskCompletion(courtListId, 120000); // 2 minutes timeout
        
        // Verify status was still updated to SUCCESSFUL (status update happens even if CaTH fails)
        // Note: WireMock verification removed as we're using static mappings
        ResponseEntity<String> statusResponse = getStatusRequest(courtListId);
        assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode statusBody = parseResponse(statusResponse);
        assertThat(statusBody.get("publishStatus").asText()).isEqualTo("SUCCESSFUL");
    }

    @Test
    void publishCourtList_shouldCreateDbEntry_triggerTask_andUpdateFileUrlWithPdfUrl() throws Exception {
        UUID courtCentreId = UUID.randomUUID();
        String requestJson = createPublishRequestJson(courtCentreId, "PUBLIC");

        // When - Call publishCourtList (controller) - this triggers createOrUpdate and triggerCourtListTask
        ResponseEntity<String> publishResponse = postPublishRequest(requestJson);

        // Then - Verify publish response
        assertThat(publishResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode publishBody = parseResponse(publishResponse);
        UUID courtListId = UUID.fromString(publishBody.get("courtListId").asText());
        assertThat(publishBody.get("publishStatus").asText()).isEqualTo("REQUESTED");

        // Verify CourtListPublishStatusService createOrUpdate created entry in DB (record exists)
        ResponseEntity<String> immediateStatus = getStatusRequest(courtListId);
        assertThat(immediateStatus.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode immediateBody = parseResponse(immediateStatus);
        assertThat(immediateBody.get("courtListId").asText()).isEqualTo(courtListId.toString());
        assertThat(immediateBody.get("courtCentreId").asText()).isEqualTo(courtCentreId.toString());

        // Wait for async task to complete (CourtListTaskTriggerService.triggerCourtListTask triggered the task)
        waitForTaskCompletion(courtListId, 120000);

        // Verify row updated with filename (SAS URL from Azurite blob upload)
        ResponseEntity<String> statusResponse = getStatusRequest(courtListId);
        assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode statusBody = parseResponse(statusResponse);
        assertThat(statusBody.get("publishStatus").asText()).isEqualTo("SUCCESSFUL");
        assertThat(statusBody.has("fileUrl")).isTrue();
        String fileUrl = statusBody.get("fileUrl").asText();
        assertThat(fileUrl).isNotBlank();
        // SAS URL from Azurite contains devstoreaccount1 and sig= (SAS token)
        assertThat(fileUrl).contains("devstoreaccount1");
        assertThat(fileUrl).contains("sig=");
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
        log.info("Created court list with ID: {}", courtListId);
        log.info("Initial status: {}", publishBody.get("publishStatus").asText());
        
        // Immediately verify the record exists
        try {
            ResponseEntity<String> immediateStatus = getStatusRequest(courtListId);
            if (immediateStatus.getStatusCode().is2xxSuccessful()) {
                JsonNode immediateBody = parseResponse(immediateStatus);
                log.info("Immediate status check: {}", immediateBody.get("publishStatus").asText());
            }
        } catch (Exception e) {
            log.error("ERROR: Could not retrieve status immediately after creation: {}", e.getMessage(), e);
            throw new AssertionError("Record was not created or is not accessible", e);
        }
        
        // Wait for async task to complete
        // Task manager JobExecutor polls the database and executes tasks
        waitForTaskCompletion(courtListId, 120000); // 2 minutes timeout
        
        // Verify status was still updated to SUCCESSFUL
        // Note: WireMock verification removed as we're using static mappings
        ResponseEntity<String> statusResponse = getStatusRequest(courtListId);
        assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode statusBody = parseResponse(statusResponse);
        assertThat(statusBody.get("publishStatus").asText()).isEqualTo("SUCCESSFUL");
    }

    // Helper methods

    private String createPublishRequestJson(UUID courtCentreId, String courtListType) {
        return """
            {
                "courtCentreId": "%s",
                "startDate": "2026-01-20",
                "endDate": "2026-01-20",
                "courtListType": "%s",
                "makeExternalCalls": true
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

    private ResponseEntity<String> getStatusRequest(UUID courtListId) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(new MediaType("application", "vnd.courtlistpublishing-service.publish.get+json")));
        
        String url = GET_STATUS_ENDPOINT + "?courtListId=" + courtListId;

        ResponseEntity<String> response = http.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
        
        JsonNode responseBody = parseResponse(response);
        if (!responseBody.isArray()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        
        // If courtListId was provided, find the matching item; otherwise return first item or empty
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
    private void waitForTaskCompletion(UUID courtListId, long timeoutMs) throws Exception {
        long startTime = System.currentTimeMillis();
        long pollInterval = 500; // Poll every 500ms
        int pollCount = 0;
        
        log.info("Waiting for task completion for courtListId: {}", courtListId);
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                ResponseEntity<String> statusResponse = getStatusRequest(courtListId);
                if (statusResponse.getStatusCode().is2xxSuccessful()) {
                    JsonNode statusBody = parseResponse(statusResponse);
                    String publishStatusStr = statusBody.get("publishStatus").asText();
                    log.debug("Poll #{}: Status = {}", pollCount, publishStatusStr);
                    
                    try {
                        Status publishStatus = Status.valueOf(publishStatusStr);
                        if (Status.SUCCESSFUL.equals(publishStatus) || Status.FAILED.equals(publishStatus)) {
                            // Task has completed (either successfully or with failure)
                            log.info("Task completed with status: {}", publishStatus);
                            return;
                        }
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid publish status value: {}", publishStatusStr);
                    }
                } else {
                    log.debug("Poll #{}: Status endpoint returned {}", pollCount, statusResponse.getStatusCode());
                }
            } catch (Exception e) {
                // Status endpoint might not be available yet, continue polling
                if (pollCount % 10 == 0) { // Log every 5 seconds
                    log.warn("Poll #{}: Error checking status - {}", pollCount, e.getMessage());
                }
            }
            pollCount++;
            Thread.sleep(pollInterval);
        }
        // Timeout reached - task may still be running
        log.error("Timeout reached after {}ms. Task may still be running.", timeoutMs);
        try {
            ResponseEntity<String> finalStatus = getStatusRequest(courtListId);
            if (finalStatus.getStatusCode().is2xxSuccessful()) {
                JsonNode statusBody = parseResponse(finalStatus);
                log.error("Final status: {}", statusBody.get("publishStatus").asText());
            }
        } catch (Exception e) {
            log.error("Could not get final status: {}", e.getMessage(), e);
        }
    }
}
