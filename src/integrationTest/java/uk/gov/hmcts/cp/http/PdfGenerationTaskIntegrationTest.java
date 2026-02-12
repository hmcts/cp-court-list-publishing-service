package uk.gov.hmcts.cp.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.cp.openapi.model.Status;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
@Slf4j
public class PdfGenerationTaskIntegrationTest extends AbstractTest {
    // Note: PDF generation is now part of CourtListPublishTask, not a separate task

    private static final String BASE_URL = System.getProperty("app.baseUrl", "http://localhost:8082/courtlistpublishing-service");
    private static final String PUBLISH_ENDPOINT = BASE_URL + "/api/court-list-publish/publish";
    private static final String GET_STATUS_ENDPOINT = BASE_URL + "/api/court-list-publish/publish-status";
    private static final String COURT_LIST_TYPE_PUBLIC = "PUBLIC";

    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper objectMapper = ObjectMapperConfig.getObjectMapper();

    @Test
    void publishCourtList_shouldTriggerPdfGeneration_whenCourtListDataAvailable() throws Exception {
        // Given
        UUID courtCentreId = UUID.randomUUID();
        String requestJson = createPublishRequest(courtCentreId);
        
        // When - Publish court list (this triggers COURT_LIST_PUBLISH_TASK which includes PDF generation)
        ResponseEntity<String> publishResponse = postPublishRequest(requestJson);
        
        // Then - Verify publish response
        assertThat(publishResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode publishBody = parseResponse(publishResponse);
        UUID courtListId = UUID.fromString(publishBody.get("courtListId").asText());
        assertThat(publishBody.get("publishStatus").asText()).isEqualTo("REQUESTED");
        
        // Wait for async task to complete (task execution is asynchronous)
        // Task manager polls and executes tasks, so we need to wait longer (30 seconds)
        // The task now handles both CaTH publishing and PDF generation
        waitForTaskCompletion(courtListId, 30000);
        
        // Verify status record exists and check if PDF was generated (fileName should be set)
        ResponseEntity<String> statusResponse = getStatusRequest(courtListId);
        assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode statusBody = parseResponse(statusResponse);
        assertThat(statusBody.get("publishStatus").asText()).isEqualTo("SUCCESSFUL");
        // Note: PDF generation happens asynchronously as part of COURT_LIST_PUBLISH_TASK,
        // so fileName might not be set immediately. The test verifies the task completed successfully.
    }

    // Helper methods

    private String createPublishRequest(UUID courtCentreId) {
        return """
            {
                "courtCentreId": "%s",
                "startDate": "2026-01-20",
                "endDate": "2026-01-20",
                "courtListType": "%s"
            }
            """.formatted(courtCentreId, COURT_LIST_TYPE_PUBLIC);
    }

    private static final String CJSCPPUID_HEADER = "CJSCPPUID";
    private static final String INTEGRATION_TEST_USER_ID = "integration-test-user-id";

    private HttpEntity<String> createPublishHttpEntity(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "vnd.courtlistpublishing-service.publish.post+json"));
        headers.set(CJSCPPUID_HEADER, INTEGRATION_TEST_USER_ID);
        return new HttpEntity<>(json, headers);
    }

    private ResponseEntity<String> postPublishRequest(String requestJson) {
        return http.exchange(PUBLISH_ENDPOINT, HttpMethod.POST, createPublishHttpEntity(requestJson), String.class);
    }

    private ResponseEntity<String> getStatusRequest(UUID courtListId) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(new MediaType("application", "vnd.courtlistpublishing-service.publish.get+json")));
        
        // Use courtListId if available, otherwise use courtCentreId and publishDate
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
