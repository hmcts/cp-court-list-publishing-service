package uk.gov.hmcts.cp.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

/**
 * {@code POST /api/court-list-publish/crown/publish} against the running stack: the crown list is fetched from
 * listing (WireMock, {@code application/vnd.listing.search.crown.list.payload+json}), turned into the CaTH PDDA
 * list and posted to CaTH (WireMock).
 */
public class CrownListPublishIntegrationTest extends CourtListIntegrationTestBase {

    /** Crown court centre in the reference data stubs (oucodeL1Code "C", courtId "001"). */
    private static final UUID CROWN_COURT_CENTRE_ID = UUID.fromString("cc000000-0000-0000-0000-000000000001");
    /**
     * Second crown court centre in the reference data stubs (courtId "002"). Listing answers it after a 10 second
     * delay (stub listing-crown-list-payload-delayed.json), so its publish stays in progress long enough for a
     * second request to arrive, even on a slow build agent.
     */
    private static final UUID SLOW_CROWN_COURT_CENTRE_ID = UUID.fromString("cc000000-0000-0000-0000-000000000002");
    private static final String CROWN_PUBLISH_URL =
            System.getProperty("app.baseUrl", "http://localhost:8082/courtlistpublishing-service")
                    + "/api/court-list-publish/crown/publish";
    private static final MediaType CROWN_POST = MediaType.parseMediaType("application/vnd.courtlistpublishing-service.crown.post+json");

    @ParameterizedTest
    @CsvSource({
            "FINAL, 2026-06-09, 2026-06-09, CROWN_DAILY_PDDA_LIST, DailyList",
            "FIRM, 2026-06-08, 2026-06-14, CROWN_FIRM_PDDA_LIST, FirmList",
            "WARN, 2026-06-08, 2026-06-14, CROWN_ADVANCE_PDDA_LIST, WarnedList"})
    void publishesTheCrownListToCaTH(String courtListType, String startDate, String endDate,
                                     String cathListType, String rootElement) throws Exception {
        ResponseEntity<String> response = post(CROWN_COURT_CENTRE_ID, courtListType, startDate, endDate);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(CROWN_POST);
        UUID courtListId = UUID.fromString(parseResponse(response).get("courtListId").asText());

        waitForTaskCompletion(courtListId, TASK_TIMEOUT_MS);
        assertThat(parseResponse(getStatusRequest(courtListId)).get("publishStatus").asText()).isEqualTo("SUCCESSFUL");

        JsonNode cathRequest = waitForCaTHRequest(rootElement, courtListId);
        assertThat(header(cathRequest, "x-list-type")).isEqualTo(cathListType);
        assertThat(header(cathRequest, "x-court-id")).isEqualTo("001");
        assertThat(header(cathRequest, "x-sensitivity")).isEqualTo("PUBLIC");
        assertThat(header(cathRequest, "x-language")).isEqualTo("WELSH");
        JsonNode list = objectMapper.readTree(body(cathRequest)).get(rootElement);
        assertThat(list.at("/ListHeader/StartDate").asText()).isEqualTo(startDate);
        assertThat(list.at("/CrownCourt/CourtHouseCode").asInt()).isEqualTo(100);
        assertThat(list.toString()).contains("T20260002");
    }

    @Test
    void refusesASecondPublishOfTheSameListWhileTheFirstIsInProgress() throws Exception {
        ResponseEntity<String> first = post(SLOW_CROWN_COURT_CENTRE_ID, "FINAL", "2026-06-09", "2026-06-09");
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID courtListId = UUID.fromString(parseResponse(first).get("courtListId").asText());

        // the first publish waits 10 seconds on listing, so its row is still REQUESTED (and locked against a duplicate)
        assertThatThrownBy(() -> post(SLOW_CROWN_COURT_CENTRE_ID, "FINAL", "2026-06-09", "2026-06-09"))
                .isInstanceOfSatisfying(HttpClientErrorException.BadRequest.class,
                        e -> assertThat(e.getResponseBodyAsString()).contains("already in progress"));

        // the first publish still completes, and once it has, the list can be published again
        waitForTaskCompletion(courtListId, TASK_TIMEOUT_MS);
        assertThat(parseResponse(getStatusRequest(courtListId)).get("publishStatus").asText()).isEqualTo("SUCCESSFUL");
        ResponseEntity<String> again = post(SLOW_CROWN_COURT_CENTRE_ID, "FINAL", "2026-06-09", "2026-06-09");
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parseResponse(again).get("courtListId").asText()).isEqualTo(courtListId.toString());
        waitForTaskCompletion(courtListId, TASK_TIMEOUT_MS);
    }

    @Test
    void rejectsACourtCentreThatIsNotACrownCourt() {
        // the default reference data stub is a magistrates' court (oucodeL1Code "B")
        assertThatThrownBy(() -> post(UUID.randomUUID(), "FINAL", "2026-06-09", "2026-06-09"))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    @Test
    void rejectsAListTypeThatIsNotACrownList() {
        assertThatThrownBy(() -> post(CROWN_COURT_CENTRE_ID, "STANDARD", "2026-06-09", "2026-06-09"))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    @Test
    void rejectsAWeekListThatDoesNotStartOnAMonday() {
        assertThatThrownBy(() -> post(CROWN_COURT_CENTRE_ID, "FIRM", "2026-06-09", "2026-06-14"))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    @Test
    void theStandardPublishRefersCrownListsToTheCrownEndpoint() {
        assertThatThrownBy(() -> postPublishRequest(createPublishRequestJson(CROWN_COURT_CENTRE_ID, "FINAL")))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    private ResponseEntity<String> post(UUID courtCentreId, String courtListType, String startDate, String endDate) {
        String json = """
            {"courtCentreId": "%s", "courtListType": "%s", "startDate": "%s", "endDate": "%s"}
            """.formatted(courtCentreId, courtListType, startDate, endDate);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(CROWN_POST);
        headers.set(CJSCPPUID_HEADER, INTEGRATION_TEST_USER_ID);
        return http.exchange(CROWN_PUBLISH_URL, HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
    }

    private JsonNode waitForCaTHRequest(String rootElement, UUID courtListId) throws Exception {
        long deadline = System.currentTimeMillis() + CATH_BODY_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            for (JsonNode entry : objectMapper.readTree(http.getForObject(WIREMOCK_ADMIN_REQUESTS, String.class)).path("requests")) {
                JsonNode request = entry.has("request") ? entry.get("request") : entry;
                String body = body(request);
                if ("POST".equalsIgnoreCase(request.path("method").asText())
                        && request.path("url").asText().contains(CATH_PUBLICATION_URL_PATH)
                        && body != null && body.contains("\"" + rootElement + "\"") && body.contains(courtListId.toString())) {
                    return request;
                }
            }
            Thread.sleep(WIREMOCK_POLL_MS);
        }
        throw new AssertionError("No CaTH " + rootElement + " publication for courtListId " + courtListId);
    }

    private static String body(JsonNode request) {
        String body = request.path("body").asText("");
        if (!body.isBlank()) {
            return body;
        }
        String base64 = request.path("bodyAsBase64").asText("");
        return base64.isBlank() ? null : new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }

    private static String header(JsonNode request, String name) {
        for (Map.Entry<String, JsonNode> header : request.path("headers").properties()) {
            if (header.getKey().equalsIgnoreCase(name)) {
                JsonNode value = header.getValue();
                return value.isArray() ? value.get(0).asText() : value.asText();
            }
        }
        return null;
    }
}
