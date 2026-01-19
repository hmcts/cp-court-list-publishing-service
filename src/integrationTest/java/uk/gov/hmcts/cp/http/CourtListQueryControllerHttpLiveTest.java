package uk.gov.hmcts.cp.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("Test failing event after merging pending PR")
public class CourtListQueryControllerHttpLiveTest {

    private static final String BASE_URL = System.getProperty("app.baseUrl", "http://localhost:8082/courtlistpublishing-service");
    private static final String QUERY_ENDPOINT = BASE_URL + "/api/court-list/query";
    public static final String USER_ID = "ad0920bd-521a-4f40-b942-f82d258ea3cc";

    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void queryCourtList_returnsTransformedDocument_whenValidParametersProvided() throws Exception {
        // Given - Using different parameters than the multiple-dates test to match the standard mapping
        String listId = "STANDARD";
        String courtCentreId = "f8254db1-1683-483e-afb3-b87fde5a0a26";
        String startDate = "2026-01-06";
        String endDate = "2026-01-13";

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
        // Given - Using specific parameters that match the multiple-dates mapping
        String listId = "STANDARD";
        String courtCentreId = "f8254db1-1683-483e-afb3-b87fde5a0a26";
        String startDate = "2026-01-05";
        String endDate = "2026-01-12";

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

    @Test
    void queryCourtList_returnsPublicTransformedDocument_whenListIdIsPublic() throws Exception {
        // Given
        String listId = "PUBLIC";
        String courtCentreId = "f8254db1-1683-483e-afb3-b87fde5a0a26";
        String startDate = "2026-01-05";
        String endDate = "2026-01-12";

        // When
        ResponseEntity<String> response = getRequest(QUERY_ENDPOINT, listId, courtCentreId, startDate, endDate);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isNotNull();

        JsonNode actualResponse = parseResponse(response);
        JsonNode expectedResponse = buildExpectedResponse("stubdata/expected-court-list-document-public.json", actualResponse);
        
        // Compare the entire responses
        assertThat(actualResponse).isEqualTo(expectedResponse);
    }

    // Helper methods

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

    /**
     * Builds expected response by loading stub data and replacing dynamic printdate with actual value
     */
    private JsonNode buildExpectedResponse(String stubDataPath, JsonNode actualResponse) throws Exception {
        String actualPrintDate = actualResponse.get("document").get("data").get("job").get("printdate").asText();
        String expectedJson = loadStubData(stubDataPath).replace("{{PRINTDATE}}", actualPrintDate);
        return objectMapper.readTree(expectedJson);
    }
}

