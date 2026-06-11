package uk.gov.hmcts.cp.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests asserting the exact CaTH payload structure for SJP press list and publish list.
 *
 * <p>These tests hit the running app synchronously (no async task) then inspect
 * what was POSTed to WireMock's /courtlistpublisher/publication using the WireMock admin API.
 * They verify the full hierarchy:
 * {@code document → courtLists[0].courtHouse.courtRoom[0].session[0].sittings[0].hearing[n].case[0]}
 * and that CaTH schema constraints are satisfied (required "case" array, UTC publicationDate).
 */
public class SjpCaTHPayloadIntegrationTest extends AbstractTest {

    private static final String SJP_PUBLISH_ENDPOINT =
            System.getProperty("app.baseUrl", "http://localhost:8082/courtlistpublishing-service")
            + "/api/court-list-publish/sjp/publishCourtList";

    private static final MediaType SJP_CONTENT_TYPE =
            new MediaType("application", "vnd.courtlistpublishing-service.sjp.post+json");

    /** Regex matching CaTH publicationDate: yyyy-MM-ddTHH:mm:ss[.nanos]Z */
    private static final String CATH_DATE_REGEX = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?Z";

    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper objectMapper = ObjectMapperConfig.getObjectMapper();

    @BeforeEach
    void resetRequests() {
        // Clear captured WireMock requests before each test so assertions are isolated
        http.exchange(WIREMOCK_ADMIN_REQUESTS, HttpMethod.DELETE,
                new HttpEntity<>(new HttpHeaders()), String.class);
    }

    // -------------------------------------------------------------------------
    // Press list — full realistic payload (mirrors the prod curl)
    // -------------------------------------------------------------------------

    @Test
    void pressListPublish_cathPayload_hasCorrectDocumentFields() throws Exception {
        postSjpRequest(fullPressListJson());

        JsonNode cath = waitForLatestCaTHRequest();
        JsonNode doc = cath.path("document");

        assertThat(doc.path("documentName").asText()).isEqualTo("SJP Press list");
        assertThat(doc.path("version").asText()).isEqualTo("1.0");
        // publicationDate must end with Z (CaTH regex requirement)
        assertThat(doc.path("publicationDate").asText())
                .matches(CATH_DATE_REGEX);
    }

    @Test
    void pressListPublish_cathPayload_hasExpectedCourtListHierarchy() throws Exception {
        postSjpRequest(fullPressListJson());

        JsonNode cath = waitForLatestCaTHRequest();

        assertThat(cath.path("courtLists").isArray()).isTrue();
        assertThat(cath.path("courtLists")).hasSize(1);

        JsonNode courtRoom = cath.path("courtLists").path(0)
                .path("courtHouse").path("courtRoom").path(0);
        assertThat(courtRoom.isMissingNode()).isFalse();

        JsonNode session = courtRoom.path("session").path(0);
        assertThat(session.isMissingNode()).isFalse();

        JsonNode sittings = session.path("sittings").path(0);
        assertThat(sittings.isMissingNode()).isFalse();

        // Two readyCases → two hearings
        JsonNode hearings = sittings.path("hearing");
        assertThat(hearings.isArray()).isTrue();
        assertThat(hearings).hasSize(2);
    }

    @Test
    void pressListPublish_cathPayload_eachHearingHasRequiredCaseArray() throws Exception {
        postSjpRequest(fullPressListJson());

        JsonNode cath = waitForLatestCaTHRequest();
        JsonNode hearings = hearings(cath);

        for (int i = 0; i < hearings.size(); i++) {
            JsonNode caseArray = hearings.path(i).path("case");
            assertThat(caseArray.isArray())
                    .as("hearing[%d] must have a 'case' array", i)
                    .isTrue();
            assertThat(caseArray.size())
                    .as("hearing[%d] 'case' array must not be empty", i)
                    .isGreaterThan(0);
        }
    }

    @Test
    void pressListPublish_cathPayload_caseUrnsMatchInput() throws Exception {
        postSjpRequest(fullPressListJson());

        JsonNode cath = waitForLatestCaTHRequest();
        JsonNode hearings = hearings(cath);

        List<String> caseUrns = new ArrayList<>();
        for (JsonNode hearing : hearings) {
            for (JsonNode c : hearing.path("case")) {
                caseUrns.add(c.path("caseUrn").asText());
            }
        }

        assertThat(caseUrns).containsExactlyInAnyOrder("TFL901845675", "TFL905582011");
    }

    @Test
    void pressListPublish_cathPayload_defendantPartiesPresent() throws Exception {
        postSjpRequest(fullPressListJson());

        JsonNode cath = waitForLatestCaTHRequest();
        JsonNode hearings = hearings(cath);

        // Hearing 0 — David Smith (individual defendant + prosecutor)
        JsonNode hearing0Parties = hearings.path(0).path("party");
        assertThat(hearing0Parties.isArray()).isTrue();
        assertThat(partyRoles(hearing0Parties)).contains("PROSECUTOR", "ACCUSED");

        // Hearing 1 — Jane Doe
        JsonNode hearing1Parties = hearings.path(1).path("party");
        assertThat(hearing1Parties.isArray()).isTrue();
        assertThat(partyRoles(hearing1Parties)).contains("PROSECUTOR", "ACCUSED");
    }

    @Test
    void pressListPublish_cathPayload_offencesIncludeReportingRestriction() throws Exception {
        postSjpRequest(fullPressListJson());

        JsonNode cath = waitForLatestCaTHRequest();
        JsonNode hearings = hearings(cath);

        // Hearing 0, offence 0 — reportingRestriction: false → null/absent on press list (NON_NULL)
        // Hearing 0, offence 1 — reportingRestriction: true → must be present
        JsonNode offence0 = hearings.path(0).path("offence").path(0);
        assertThat(offence0.path("offenceTitle").asText()).isEqualTo("Occupy reserved seat without a valid ticket");

        JsonNode offence1 = hearings.path(0).path("offence").path(1);
        assertThat(offence1.path("offenceTitle").asText()).isEqualTo("No insurance");
        assertThat(offence1.path("reportingRestriction").asBoolean()).isTrue();
    }

    @Test
    void pressListPublish_cathPayload_individualDetailsPopulated() throws Exception {
        postSjpRequest(fullPressListJson());

        JsonNode cath = waitForLatestCaTHRequest();
        JsonNode hearings = hearings(cath);

        // Find the ACCUSED individual party for David Smith in hearing 0
        JsonNode accusedParty = findPartyByRole(hearings.path(0).path("party"), "ACCUSED", true);
        assertThat(accusedParty).isNotNull();
        JsonNode ind = accusedParty.path("individualDetails");
        assertThat(ind.path("individualForenames").asText()).isEqualTo("David");
        assertThat(ind.path("individualSurname").asText()).isEqualTo("Smith");
        assertThat(ind.path("dateOfBirth").asText()).isEqualTo("1980-07-15");
        assertThat(ind.path("address").path("postCode").asText()).isEqualTo("CB1 1BG");
    }

    @Test
    void pressListPublish_returns200_withAcceptedStatus() throws Exception {
        ResponseEntity<String> response = postSjpRequest(fullPressListJson());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("status").asText()).isEqualTo("ACCEPTED");
        assertThat(body.path("listType").asText()).isEqualTo("SJP_PRESS_LIST");
    }

    // -------------------------------------------------------------------------
    // Publish list — verify same structure rules apply
    // -------------------------------------------------------------------------

    @Test
    void publishListPublish_cathPayload_eachHearingHasRequiredCaseArray() throws Exception {
        postSjpRequest(fullPublishListJson());

        JsonNode cath = waitForLatestCaTHRequest();
        JsonNode hearings = hearings(cath);

        assertThat(hearings.size()).isGreaterThan(0);
        for (int i = 0; i < hearings.size(); i++) {
            JsonNode caseArray = hearings.path(i).path("case");
            assertThat(caseArray.isArray())
                    .as("hearing[%d] must have a 'case' array", i).isTrue();
            assertThat(caseArray.size())
                    .as("hearing[%d] 'case' array must not be empty", i).isGreaterThan(0);
        }
    }

    @Test
    void publishListPublish_cathPayload_documentNameIsPublicList() throws Exception {
        postSjpRequest(fullPublishListJson());

        JsonNode cath = waitForLatestCaTHRequest();
        assertThat(cath.path("document").path("documentName").asText()).isEqualTo("SJP Public list");
    }

    @Test
    void publishListPublish_cathPayload_publicationDateEndsWithZ() throws Exception {
        // Input has no Z — transformer must append it
        postSjpRequest(fullPublishListJson());

        JsonNode cath = waitForLatestCaTHRequest();
        String publicationDate = cath.path("document").path("publicationDate").asText();
        assertThat(publicationDate).matches(CATH_DATE_REGEX);
        assertThat(publicationDate).endsWith("Z");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ResponseEntity<String> postSjpRequest(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(SJP_CONTENT_TYPE);
        return http.exchange(SJP_PUBLISH_ENDPOINT, HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
    }

    /** Polls WireMock admin API until a CaTH POST appears (synchronous SJP publish, so usually instant). */
    private JsonNode waitForLatestCaTHRequest() throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            List<JsonNode> requests = listCaTHRequests();
            if (!requests.isEmpty()) {
                String rawBody = requests.getLast().path("body").asText(null);
                if (rawBody == null || rawBody.isBlank()) {
                    // body may be base64 encoded
                    rawBody = requests.getLast().path("bodyAsBase64").asText(null);
                    if (rawBody != null) {
                        rawBody = new String(java.util.Base64.getDecoder().decode(rawBody));
                    }
                }
                assertThat(rawBody).as("CaTH request body must not be blank").isNotBlank();
                return objectMapper.readTree(rawBody);
            }
            Thread.sleep(200);
        }
        throw new AssertionError("No CaTH POST captured by WireMock within 10s");
    }

    private List<JsonNode> listCaTHRequests() throws Exception {
        ResponseEntity<String> admin = http.exchange(WIREMOCK_ADMIN_REQUESTS, HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), String.class);
        JsonNode root = objectMapper.readTree(admin.getBody());
        JsonNode requests = root.isArray() ? root : root.path("requests");
        List<JsonNode> result = new ArrayList<>();
        if (requests.isArray()) {
            for (JsonNode req : requests) {
                String url = req.path("request").path("url").asText("");
                if (url.contains(CATH_PUBLICATION_URL_PATH)) {
                    result.add(req.path("request"));
                }
            }
        }
        return result;
    }

    private static JsonNode hearings(JsonNode cathPayload) {
        return cathPayload.path("courtLists").path(0)
                .path("courtHouse").path("courtRoom").path(0)
                .path("session").path(0)
                .path("sittings").path(0)
                .path("hearing");
    }

    private static List<String> partyRoles(JsonNode parties) {
        List<String> roles = new ArrayList<>();
        for (JsonNode p : parties) {
            roles.add(p.path("partyRole").asText());
        }
        return roles;
    }

    /** Returns the first party with the given role; {@code individual=true} restricts to individual defendants. */
    private static JsonNode findPartyByRole(JsonNode parties, String role, boolean individual) {
        for (JsonNode p : parties) {
            if (!role.equals(p.path("partyRole").asText())) {
                continue;
            }
            if (individual && p.path("individualDetails").isMissingNode()) {
                continue;
            }
            return p;
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Test payloads
    // -------------------------------------------------------------------------

    private static String fullPressListJson() {
        return """
            {
              "listType": "SJP_PRESS_LIST",
              "language": "ENGLISH",
              "requestType": "FULL",
              "listPayload": {
                "generatedDateAndTime": "2026-06-11T09:30:00",
                "isWelsh": false,
                "courtIdNumeric": "325",
                "readyCases": [
                  {
                    "caseUrn": "TFL901845675",
                    "prosecutorName": "Transport for London",
                    "legalEntityName": "Acme Haulage Ltd",
                    "defendantName": "David Smith",
                    "title": "Mr",
                    "firstName": "David",
                    "lastName": "Smith",
                    "dateOfBirth": "1980-07-15",
                    "age": "45",
                    "addressLine1": "12 East Road",
                    "addressLine2": "Flat 4",
                    "addressLine3": "",
                    "town": "Cambridge",
                    "country": "Cambridgeshire",
                    "postcode": "CB1 1BG",
                    "sjpOffences": [
                      {
                        "title": "Occupy reserved seat without a valid ticket",
                        "wording": "Travelled on the Tyne and Wear Metro without a valid ticket",
                        "reportingRestriction": false
                      },
                      {
                        "title": "No insurance",
                        "wording": "Using a motor vehicle without third party insurance",
                        "reportingRestriction": true
                      }
                    ]
                  },
                  {
                    "caseUrn": "TFL905582011",
                    "prosecutorName": "TV Licensing",
                    "defendantName": "Jane Doe",
                    "title": "Ms",
                    "firstName": "Jane",
                    "lastName": "Doe",
                    "dateOfBirth": "1985-07-15",
                    "age": "40",
                    "addressLine1": "45 High Street",
                    "town": "Ely",
                    "country": "Cambridgeshire",
                    "postcode": "CB7 4JU",
                    "sjpOffences": [
                      {
                        "title": "TV licence evasion",
                        "wording": "Using a television receiver without a licence",
                        "reportingRestriction": true
                      }
                    ]
                  }
                ]
              }
            }
            """;
    }

    private static String fullPublishListJson() {
        return """
            {
              "listType": "SJP_PUBLISH_LIST",
              "language": "ENGLISH",
              "requestType": "FULL",
              "listPayload": {
                "generatedDateAndTime": "2026-06-11T09:30:00",
                "isWelsh": false,
                "courtIdNumeric": "325",
                "readyCases": [
                  {
                    "caseUrn": "SJP-PUB-001",
                    "prosecutorName": "CPS",
                    "defendantName": "Alice Jones",
                    "title": "Ms",
                    "firstName": "Alice",
                    "lastName": "Jones",
                    "dateOfBirth": "1990-03-22",
                    "age": "36",
                    "addressLine1": "10 Mill Lane",
                    "town": "Norwich",
                    "country": "Norfolk",
                    "postcode": "NR1 1AB",
                    "sjpOffences": [
                      {
                        "title": "Speeding",
                        "wording": "Exceeded the speed limit on a public road",
                        "reportingRestriction": false
                      }
                    ]
                  }
                ]
              }
            }
            """;
    }
}
