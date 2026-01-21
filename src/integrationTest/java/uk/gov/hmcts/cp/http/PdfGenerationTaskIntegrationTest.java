package uk.gov.hmcts.cp.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

import java.net.BindException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
public class PdfGenerationTaskIntegrationTest {

    private static final String BASE_URL = System.getProperty("app.baseUrl", "http://localhost:8082/courtlistpublishing-service");
    private static final String PUBLISH_ENDPOINT = BASE_URL + "/api/court-list-publish/publish";
    private static final String GET_STATUS_ENDPOINT = BASE_URL + "/api/court-list-publish/court-centre/";
    private static final String COURT_LIST_TYPE_PUBLIC = "PUBLIC";
    private static final int WIREMOCK_PORT = 8089;

    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private static WireMockServer wireMockServer;
    private static boolean wireMockInitialized = false;

    static {
        initializeWireMock();
    }

    private static void initializeWireMock() {
        try {
            WireMockConfiguration config = WireMockConfiguration.options()
                    .port(WIREMOCK_PORT)
                    .bindAddress("0.0.0.0")
                    .asynchronousResponseEnabled(true)
                    .asynchronousResponseThreads(10);
            
            wireMockServer = new WireMockServer(config);

            try {
                wireMockServer.start();
                wireMockInitialized = true;
                System.out.println("✓ WireMock server started on port " + WIREMOCK_PORT);
                
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    if (wireMockServer != null && wireMockServer.isRunning() && wireMockInitialized) {
                        try {
                            wireMockServer.stop();
                        } catch (Exception e) {
                            System.err.println("Warning: Failed to stop WireMock server: " + e.getMessage());
                        }
                    }
                }));
            } catch (Exception e) {
                handleWireMockStartFailure(e);
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not initialize WireMock server: " + e.getMessage());
            WireMock.configureFor("localhost", WIREMOCK_PORT);
        }
    }

    private static void handleWireMockStartFailure(Exception e) {
        Throwable cause = e.getCause();
        boolean isBindException = e instanceof BindException || 
                                 (cause != null && cause instanceof BindException);
        
        if (isBindException) {
            System.out.println("✓ WireMock port " + WIREMOCK_PORT + " already in use, reusing existing instance");
        } else {
            System.err.println("Warning: Could not start WireMock server: " + e.getMessage());
        }
        
        wireMockInitialized = false;
        WireMock.configureFor("localhost", WIREMOCK_PORT);
    }

    @BeforeEach
    void setUp() {
        if (wireMockInitialized && wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.resetAll();
            wireMockServer.resetScenarios();
        } else {
            try {
                WireMock.configureFor("localhost", WIREMOCK_PORT);
                WireMock.reset();
            } catch (Exception e) {
                System.err.println("Warning: Could not reset WireMock: " + e.getMessage());
            }
        }
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.resetAll();
        }
    }

    @Test
    void publishCourtList_shouldTriggerPdfGenerationTask_whenCourtListDataAvailable() throws Exception {
        UUID courtCentreId = UUID.randomUUID();
        String courtListPayloadJson = loadStubData("court-list-payload-public.json");
        
        configureWireMockStub(courtCentreId, courtListPayloadJson);

        String requestJson = createPublishRequest(courtCentreId);
        ResponseEntity<String> response = postRequest(requestJson);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode responseBody = parseResponse(response);
        UUID courtListId = UUID.fromString(responseBody.get("courtListId").asText());
        assertThat(courtListId).isNotNull();
        assertThat(responseBody.get("publishStatus").asText()).isEqualTo("PUBLISH_REQUESTED");

        Thread.sleep(3000);

        verifyCourtListQueryApiCalled(courtCentreId);
        verifyStatusRecordExists(courtCentreId, courtListId);
    }

    private void configureWireMockStub(UUID courtCentreId, String courtListPayloadJson) {
        var stubBuilder = get(urlMatching("/listing-query-api/query/api/rest/listing/courtlistpayload.*"))
                .withQueryParam("listId", equalTo("PUBLIC"))
                .withQueryParam("courtCentreId", equalTo(courtCentreId.toString()))
                .withQueryParam("startDate", matching(".*"))
                .withQueryParam("endDate", matching(".*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/vnd.listing.search.court.list.payload+json")
                        .withBody(courtListPayloadJson));

        if (wireMockInitialized && wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stubFor(stubBuilder);
        } else {
            WireMock.configureFor("localhost", WIREMOCK_PORT);
            WireMock.stubFor(stubBuilder);
        }
    }

    private void verifyCourtListQueryApiCalled(UUID courtCentreId) {
        try {
            var verificationBuilder = getRequestedFor(urlMatching("/listing-query-api/query/api/rest/listing/courtlistpayload.*"))
                    .withQueryParam("listId", equalTo("PUBLIC"))
                    .withQueryParam("courtCentreId", equalTo(courtCentreId.toString()))
                    .withQueryParam("startDate", matching(".*"))
                    .withQueryParam("endDate", matching(".*"));

            if (wireMockInitialized && wireMockServer != null && wireMockServer.isRunning()) {
                wireMockServer.verify(verificationBuilder);
            } else {
                WireMock.configureFor("localhost", WIREMOCK_PORT);
                WireMock.verify(verificationBuilder);
            }
        } catch (com.github.tomakehurst.wiremock.verification.RequestJournalDisabledException e) {
            System.out.println("Note: Request journal is disabled, skipping WireMock verification");
        }
    }

    private void verifyStatusRecordExists(UUID courtCentreId, UUID courtListId) throws Exception {
        ResponseEntity<String> statusResponse = getRequest(GET_STATUS_ENDPOINT + courtCentreId);
        assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        JsonNode statusBody = parseResponse(statusResponse);
        assertThat(statusBody.isArray()).isTrue();
        
        boolean found = false;
        for (JsonNode item : statusBody) {
            if (item.get("courtListId").asText().equals(courtListId.toString())) {
                found = true;
                assertThat(item.get("courtCentreId").asText()).isEqualTo(courtCentreId.toString());
                assertThat(item.get("publishStatus").asText()).isEqualTo("PUBLISH_REQUESTED");
                assertThat(item.get("courtListType").asText()).isEqualTo(COURT_LIST_TYPE_PUBLIC);
                break;
            }
        }
        assertThat(found).isTrue();
    }

    private String createPublishRequest(UUID courtCentreId) {
        return """
            {
                "courtCentreId": "%s",
                "startDate": "2026-01-20",
                "endDate": "2026-01-27",
                "courtListType": "%s"
            }
            """.formatted(courtCentreId, COURT_LIST_TYPE_PUBLIC);
    }

    private String loadStubData(String filename) throws Exception {
        ClassPathResource resource = new ClassPathResource("wiremock/__files/" + filename);
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
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
}
