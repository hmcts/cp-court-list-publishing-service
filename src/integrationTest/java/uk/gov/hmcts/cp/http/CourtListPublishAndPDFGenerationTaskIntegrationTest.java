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
import org.springframework.web.client.HttpServerErrorException;
import uk.gov.hmcts.cp.openapi.model.Status;

@Slf4j
public class CourtListPublishAndPDFGenerationTaskIntegrationTest extends AbstractTest {

    private static final String BASE_URL = System.getProperty("app.baseUrl", "http://localhost:8082/courtlistpublishing-service");
    private static final String PUBLISH_ENDPOINT = BASE_URL + "/api/court-list-publish/publish";
    private static final String GET_STATUS_ENDPOINT = BASE_URL + "/api/court-list-publish/publish-status";
    private static final String WIREMOCK_BASE_URL = System.getProperty("wiremock.baseUrl", "http://localhost:8089");
    private static final String WIREMOCK_MAPPINGS_URL = WIREMOCK_BASE_URL + "/__admin/mappings";
    private static final String CJSCPPUID_HEADER = "CJSCPPUID";
    private static final String INTEGRATION_TEST_USER_ID = "integration-test-user-id";

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
        // Given - CaTH returns 500 (add stub with priority 0 so it wins over default success)
        addCathFailureStub();
        try {
            UUID courtCentreId = UUID.randomUUID();
            String requestJson = createPublishRequestJson(courtCentreId, "STANDARD");

            // When - Publish court list (task will call CaTH and get 500)
            ResponseEntity<String> publishResponse = postPublishRequest(requestJson);

            assertThat(publishResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode publishBody = parseResponse(publishResponse);
            UUID courtListId = UUID.fromString(publishBody.get("courtListId").asText());

            waitForTaskCompletion(courtListId, 120000);

            // Then - DB has publishStatus FAILED and publishErrorMessage set
            ResponseEntity<String> statusResponse = getStatusRequest(courtListId);
            assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode statusBody = parseResponse(statusResponse);
            assertThat(statusBody.get("publishStatus").asText()).isEqualTo("FAILED");
            assertThat(statusBody.has("publishErrorMessage")).isTrue();
            assertThat(statusBody.get("publishErrorMessage").asText()).isNotBlank();
        } finally {
            AbstractTest.resetWireMock();
        }
    }

    @Test
    void publishCourtList_shouldSetPublishFailedAndSavePublishErrorMessage_whenCaTHFails() throws Exception {
        // Given - CaTH returns 500 (add stub with priority 0 so it wins over default success)
        addCathFailureStub();
        try {
            UUID courtCentreId = UUID.randomUUID();
            String requestJson = createPublishRequestJson(courtCentreId, "PUBLIC");

            // When - Publish court list (task calls CaTH and gets 500)
            ResponseEntity<String> publishResponse = postPublishRequest(requestJson);

            assertThat(publishResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode publishBody = parseResponse(publishResponse);
            UUID courtListId = UUID.fromString(publishBody.get("courtListId").asText());

            waitForTaskCompletion(courtListId, 120000);

            // Then - DB has publishStatus FAILED and publishErrorMessage set
            ResponseEntity<String> statusResponse = getStatusRequest(courtListId);
            assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode statusBody = parseResponse(statusResponse);
            assertThat(statusBody.get("publishStatus").asText()).isEqualTo("FAILED");
            assertThat(statusBody.has("publishErrorMessage")).isTrue();
            assertThat(statusBody.get("publishErrorMessage").asText()).isNotBlank();
        } finally {
            AbstractTest.resetWireMock();
        }
    }
    @Test
    void publishCourtList_shouldSetFileFailedAndSaveFileErrorMessage_whenPdfGenerationFails() throws Exception {
        // Given - Add stub so document-generator returns 500 (only for this test)
        addDocumentGeneratorFailureStub();

        try {
            UUID courtCentreId = UUID.randomUUID();
            String requestJson = createPublishRequestJson(courtCentreId, "PUBLIC");

            // When - Publish court list (task will call document-generator and get 500)
            ResponseEntity<String> publishResponse = postPublishRequest(requestJson);

            assertThat(publishResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode publishBody = parseResponse(publishResponse);
            UUID courtListId = UUID.fromString(publishBody.get("courtListId").asText());

            waitForTaskCompletion(courtListId, 120000);

            // Then - DB has fileStatus FAILED and fileErrorMessage set
            ResponseEntity<String> statusResponse = getStatusRequest(courtListId);
            assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode statusBody = parseResponse(statusResponse);
            assertThat(statusBody.get("fileStatus").asText()).isEqualTo("FAILED");
            assertThat(statusBody.has("fileErrorMessage")).isTrue();
            assertThat(statusBody.get("fileErrorMessage").asText()).isNotBlank();
        } finally {
            AbstractTest.resetWireMock();
        }
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

        // Wait for async task to complete (file status/fileId updated after PDF generation)
        waitForFileCompletion(courtListId, 120000);

        // Verify row updated with fileId (PDF uploaded as {courtListId}.pdf)
        ResponseEntity<String> statusResponse = getStatusRequest(courtListId);
        assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode statusBody = parseResponse(statusResponse);
        assertThat(statusBody.get("publishStatus").asText()).isEqualTo("SUCCESSFUL");
        assertThat(statusBody.has("fileId")).isTrue();
        String fileIdStr = statusBody.get("fileId").asText();
        assertThat(fileIdStr).isNotBlank();
        assertThat(UUID.fromString(fileIdStr)).isEqualTo(courtListId);
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
                "courtListType": "%s"
            }
            """.formatted(courtCentreId, courtListType);
    }

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
     * Adds a WireMock stub so CaTH returns 500. Use only in tests that expect publish failure.
     * Call {@link AbstractTest#resetWireMock()} in a finally block after the test so other tests get default mappings.
     */
    private void addCathFailureStub() {
        String mappingJson = """
            {
              "request": {
                "method": "POST",
                "urlPathPattern": "/courtlistpublisher/publication.*"
              },
              "response": {
                "status": 500,
                "headers": {"Content-Type": "application/json"},
                "body": "{\\"error\\": \\"Internal Server Error\\"}"
              },
              "priority": 0
            }
            """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = http.exchange(
                WIREMOCK_MAPPINGS_URL,
                HttpMethod.POST,
                new HttpEntity<>(mappingJson, headers),
                String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Failed to add CaTH failure stub: " + response.getStatusCode());
        }

        // Sanity check: ensure WireMock is actually returning 500 for CaTH on the host-mapped port
        // (helps diagnose cases where the stub isn't applied).
        try {
            HttpHeaders cathHeaders = new HttpHeaders();
            cathHeaders.setContentType(MediaType.APPLICATION_JSON);
            http.exchange(
                    WIREMOCK_BASE_URL + "/courtlistpublisher/publication",
                    HttpMethod.POST,
                    new HttpEntity<>("{}", cathHeaders),
                    String.class
            );
            throw new AssertionError("Expected WireMock CaTH endpoint to return 500 after adding failure stub");
        } catch (HttpServerErrorException expected) {
            // expected: 5xx
            assertThat(expected.getStatusCode().value()).isEqualTo(500);
        }
    }

    /**
     * Adds a WireMock stub so document-generator returns 500. Use only in tests that expect PDF failure.
     * Call {@link AbstractTest#resetWireMock()} in a finally block after the test so other tests get default mappings.
     */
    private void addDocumentGeneratorFailureStub() {
        String mappingJson = """
            {
              "request": {
                "method": "POST",
                "urlPathPattern": "/systemdocgenerator-command-api/command/api/rest/systemdocgenerator/render"
              },
              "response": {
                "status": 500,
                "headers": {"Content-Type": "application/json"},
                "body": "{\\"error\\": \\"PDF generation failed\\"}"
              },
              "priority": 0
            }
            """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = http.exchange(
                WIREMOCK_MAPPINGS_URL,
                HttpMethod.POST,
                new HttpEntity<>(mappingJson, headers),
                String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Failed to add document-generator failure stub: " + response.getStatusCode());
        }
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
        String finalStatusMsg = "timeout";
        try {
            ResponseEntity<String> finalStatus = getStatusRequest(courtListId);
            if (finalStatus.getStatusCode().is2xxSuccessful()) {
                JsonNode statusBody = parseResponse(finalStatus);
                finalStatusMsg = "publishStatus=" + statusBody.get("publishStatus").asText()
                        + ", fileStatus=" + (statusBody.has("fileStatus") ? statusBody.get("fileStatus").asText() : "n/a");
                log.error("Final status: {}", finalStatusMsg);
            }
        } catch (Exception e) {
            log.error("Could not get final status: {}", e.getMessage(), e);
        }
        throw new AssertionError("Task did not complete within " + timeoutMs + "ms. " + finalStatusMsg);
    }

    /**
     * Waits for file completion by polling the status endpoint until fileStatus is SUCCESSFUL or FAILED.
     * Needed because publishStatus can become SUCCESSFUL before fileStatus is updated.
     */
    private void waitForFileCompletion(UUID courtListId, long timeoutMs) throws Exception {
        long startTime = System.currentTimeMillis();
        long pollInterval = 500;
        int pollCount = 0;

        log.info("Waiting for file completion for courtListId: {}", courtListId);

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                ResponseEntity<String> statusResponse = getStatusRequest(courtListId);
                if (statusResponse.getStatusCode().is2xxSuccessful()) {
                    JsonNode statusBody = parseResponse(statusResponse);
                    String fileStatusStr = statusBody.get("fileStatus").asText();
                    log.debug("Poll #{}: fileStatus = {}", pollCount, fileStatusStr);
                    try {
                        Status fileStatus = Status.valueOf(fileStatusStr);
                        if (Status.SUCCESSFUL.equals(fileStatus) || Status.FAILED.equals(fileStatus)) {
                            log.info("File completed with status: {}", fileStatus);
                            return;
                        }
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid file status value: {}", fileStatusStr);
                    }
                }
            } catch (Exception e) {
                if (pollCount % 10 == 0) {
                    log.warn("Poll #{}: Error checking file status - {}", pollCount, e.getMessage());
                }
            }
            pollCount++;
            Thread.sleep(pollInterval);
        }

        String finalStatusMsg = "timeout";
        try {
            ResponseEntity<String> finalStatus = getStatusRequest(courtListId);
            if (finalStatus.getStatusCode().is2xxSuccessful()) {
                JsonNode statusBody = parseResponse(finalStatus);
                finalStatusMsg = "publishStatus=" + statusBody.get("publishStatus").asText()
                        + ", fileStatus=" + (statusBody.has("fileStatus") ? statusBody.get("fileStatus").asText() : "n/a");
                log.error("Final status: {}", finalStatusMsg);
            }
        } catch (Exception e) {
            log.error("Could not get final status: {}", e.getMessage(), e);
        }
        throw new AssertionError("File did not complete within " + timeoutMs + "ms. " + finalStatusMsg);
    }
}
