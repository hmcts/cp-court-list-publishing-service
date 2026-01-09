package uk.gov.hmcts.cp.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

public class CourtListQueryControllerHttpLiveTest {

    private static final String BASE_URL = System.getProperty("app.baseUrl", "http://localhost:8082/courtlistpublishing-service");
    private static final String QUERY_ENDPOINT = BASE_URL + "/api/court-list/query";
    public static final String USER_ID = "ad0920bd-521a-4f40-b942-f82d258ea3cc";

    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private static WireMockServer wireMockServer;
    private static int wireMockPort;
    private static final int WIREMOCK_PORT = 8089;

    static {
        try {
            WireMockConfiguration config = WireMockConfiguration.options()
                    .port(WIREMOCK_PORT)
                    .bindAddress("0.0.0.0") // Bind to all interfaces so Docker can access it
                    .disableRequestJournal() // Disable request journal for better performance
                    .asynchronousResponseEnabled(true) // Enable async responses for better performance
                    .asynchronousResponseThreads(10); // Configure thread pool for async responses
            
            wireMockServer = new WireMockServer(config);
            wireMockServer.start();
            wireMockPort = wireMockServer.port();
            
            System.out.println("✓ WireMock server started on port " + wireMockPort + " (bound to 0.0.0.0)");
            System.out.println("✓ WireMock URL for Docker: http://host.docker.internal:" + wireMockPort);
            
            // Register shutdown hook for graceful cleanup
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (wireMockServer != null && wireMockServer.isRunning()) {
                    try {
                        wireMockServer.stop();
                        System.out.println("WireMock server stopped via shutdown hook");
                    } catch (Exception e) {
                        System.err.println("Warning: Failed to stop WireMock server: " + e.getMessage());
                    }
                }
            }));
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize WireMock server", e);
        }
    }

    @BeforeEach
    void setUp() {
        // Reset WireMock stubs and scenarios for each test to ensure test isolation
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.resetAll();
            wireMockServer.resetScenarios();
            System.out.println("✓ WireMock reset for test isolation");
        }
    }

    @AfterEach
    void tearDown() {
        // Clean up stubs after each test to prevent test interference
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.resetAll();
        }
    }

    @Test
    void queryCourtList_returnsTransformedDocument_whenValidParametersProvided() throws Exception {
        // Given
        String listId = "STANDARD";
        String courtCentreId = "f8254db1-1683-483e-afb3-b87fde5a0a26";
        String startDate = "2026-01-05";
        String endDate = "2026-01-12";

        // Setup stub with mock data
        setupStubForStandardResponse(listId, courtCentreId, startDate, endDate);

        // When
        ResponseEntity<String> response = getRequest(QUERY_ENDPOINT, listId, courtCentreId, startDate, endDate);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isNotNull();

        JsonNode actualResponse = parseResponse(response);
        JsonNode expectedResponse = buildExpectedResponse("stubdata/expected-court-list-document-standard.json", actualResponse);
        
        // Compare the entire responses
        assertThat(actualResponse).isEqualTo(expectedResponse);
    }



    @Test
    void queryCourtList_handlesMultipleHearingDates() throws Exception {
        // Given
        String listId = "STANDARD";
        String courtCentreId = "f8254db1-1683-483e-afb3-b87fde5a0a26";
        String startDate = "2026-01-05";
        String endDate = "2026-01-12";

        // Setup stub with multiple hearing dates mock data
        setupStubForMultipleDatesResponse();

        // When
        ResponseEntity<String> response = getRequest(QUERY_ENDPOINT, listId, courtCentreId, startDate, endDate);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isNotNull();

        JsonNode actualResponse = parseResponse(response);
        JsonNode expectedResponse = buildExpectedResponse("stubdata/expected-court-list-document-multiple-dates.json", actualResponse);
        
        // Compare the entire responses
        assertThat(actualResponse).isEqualTo(expectedResponse);
    }

    // Helper methods - Stub setup methods

    /**
     * Sets up WireMock stub for standard response with single hearing date
     */
    private void setupStubForStandardResponse(String listId, String courtCentreId, 
                                               String startDate, String endDate) {
        String mockApiResponse = loadStubData("stubdata/court-list-payload-standard.json");
        // Use urlMatching to catch any request to this path, regardless of query parameters
        wireMockServer.stubFor(get(urlMatching("/listing-query-api/query/api/rest/listing/courtlistpayload.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(mockApiResponse)));
        System.out.println("✓ WireMock stub configured for standard response at http://host.docker.internal:" + wireMockPort);
    }

    /**
     * Sets up WireMock stub for response with multiple hearing dates
     */
    private void setupStubForMultipleDatesResponse() {
        String mockApiResponse = loadStubData("stubdata/court-list-payload-multiple-dates.json");
        // Use urlMatching to catch any request to this path, regardless of query parameters
        wireMockServer.stubFor(get(urlMatching("/listing-query-api/query/api/rest/listing/courtlistpayload.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(mockApiResponse)));
        System.out.println("✓ WireMock stub configured for multiple dates response at http://host.docker.internal:" + wireMockPort);
    }


    /**
     * Loads stub data from a resource file
     */
    private String loadStubData(String resourcePath) {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load stub data from: " + resourcePath, e);
        }
    }

    // Helper methods - HTTP request methods

    private ResponseEntity<String> getRequest(String url, String listId, String courtCentreId, 
                                               String startDate, String endDate) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.set("CJSCPPUID", USER_ID);
        
        StringBuilder urlBuilder = new StringBuilder(url);
        urlBuilder.append("?");
        if (listId != null) {
            urlBuilder.append("listId=").append(listId).append("&");
        }
        if (courtCentreId != null) {
            urlBuilder.append("courtCentreId=").append(courtCentreId).append("&");
        }
        if (startDate != null) {
            urlBuilder.append("startDate=").append(startDate).append("&");
        }
        if (endDate != null) {
            urlBuilder.append("endDate=").append(endDate);
        }
        
        return http.exchange(urlBuilder.toString(), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private JsonNode parseResponse(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    /**
     * Builds expected response by loading stub data and replacing dynamic printdate with actual value
     */
    private JsonNode buildExpectedResponse(String stubDataPath, JsonNode actualResponse) throws Exception {
        String actualPrintDate = actualResponse.get("document").get("data").get("job").get("printdate").asText();
        String expectedJson = loadStubData(stubDataPath).replace("{{PRINTDATE}}", actualPrintDate);
        return objectMapper.readTree(expectedJson);
    }
}

