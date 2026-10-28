package uk.gov.hmcts.cp.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Publishes each Crown list type through the new endpoint and checks what reached the CaTH stub: the list type CaTH
 * is told about, the metadata, and the root element of the document. The listing context's Crown court list is
 * served by WireMock ({@code courtlist-crown-list.json}).
 */
public class CrownCaTHPublishingIntegrationTest extends CourtListIntegrationTestBase {

    private static final String CROWN_PUBLISH_ENDPOINT = PUBLISH_ENDPOINT.replace("/publish", "/crown/publish");
    private static final MediaType CROWN_MEDIA_TYPE = new MediaType("application", "vnd.courtlistpublishing-service.crown.post+json");

    @Test
    void draftIsPublishedToCaThAsTheCrownDailyCrownList() throws Exception {
        publishAndVerify("DRAFT", "2026-11-02", "2026-11-02", "CROWN_DAILY_LIST", "DailyList", "DL");
    }

    @Test
    void finalIsPublishedToCaThAsTheCrownDailyCrownList() throws Exception {
        publishAndVerify("FINAL", "2026-11-02", "2026-11-02", "CROWN_DAILY_LIST", "DailyList", "DL");
    }

    @Test
    void warnIsPublishedToCaThAsTheCrownAdvanceCrownList() throws Exception {
        publishAndVerify("WARN", "2026-11-02", "2026-11-08", "CROWN_ADVANCE_LIST", "WarnedList", "WL");
    }

    @Test
    void firmIsPublishedToCaThAsTheCrownFirmCrownList() throws Exception {
        publishAndVerify("FIRM", "2026-11-02", "2026-11-08", "CROWN_FIRM_LIST", "FirmList", "FL");
    }

    @Test
    void aCourtListTypeWithNoCrownSchemaIsRejected() {
        assertThatThrownBy(() -> postCrown(UUID.randomUUID(), "STANDARD", "2026-11-02", "2026-11-02"))
                .isInstanceOfSatisfying(HttpClientErrorException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private void publishAndVerify(final String courtListType, final String startDate, final String endDate,
                                  final String cathListType, final String rootElement, final String documentType) throws Exception {
        final int before = findCaTHRequests(cathListType).size();

        final ResponseEntity<String> response = postCrown(UUID.randomUUID(), courtListType, startDate, endDate);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        final JsonNode accepted = parseResponse(response);
        assertThat(accepted.path("courtListType").asText()).isEqualTo(courtListType);
        assertThat(accepted.path("publishStatus").asText()).isEqualTo("REQUESTED");
        final UUID courtListId = UUID.fromString(accepted.get("courtListId").asText());

        waitForTaskCompletion(courtListId, TASK_TIMEOUT_MS);
        assertThat(parseResponse(getStatusRequest(courtListId)).path("publishStatus").asText()).isEqualTo("SUCCESSFUL");

        final JsonNode cathRequest = waitForAdditionalCaTHRequest(cathListType, before);
        assertThat(header(cathRequest, "x-list-type")).isEqualTo(cathListType);
        assertThat(header(cathRequest, "x-sensitivity")).isEqualTo("CLASSIFIED");
        assertThat(header(cathRequest, "x-language")).isEqualTo("ENGLISH");
        // the court id and name come from the reference data stub (referencedata-ou-courtrooms-by-courtcentreid.json)
        assertThat(header(cathRequest, "x-court-id")).isEqualTo("415");
        assertThat(header(cathRequest, "x-content-date")).startsWith(startDate);
        assertThat(header(cathRequest, "x-display-to")).startsWith(endDate);

        final JsonNode document = objectMapper.readTree(body(cathRequest));
        assertThat(document.has(rootElement)).as("root element " + rootElement).isTrue();
        final JsonNode list = document.path(rootElement);
        assertThat(list.path("DocumentID").path("DocumentType").asText()).isEqualTo(documentType);
        assertThat(list.path("ListHeader").path("StartDate").asText()).isEqualTo(startDate);
        assertThat(list.path("ListHeader").path("EndDate").asText()).isEqualTo(endDate);
        assertThat(list.path("CrownCourt").path("CourtHouseName").asText()).isEqualTo("Test Court");
        assertThat(list.path("CrownCourt").path("CourtHouseCode").asInt()).isEqualTo(415);
        assertThat(list.path("CourtLists")).isNotEmpty();
    }

    private ResponseEntity<String> postCrown(final UUID courtCentreId, final String courtListType,
                                            final String startDate, final String endDate) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(CROWN_MEDIA_TYPE);
        headers.set(CJSCPPUID_HEADER, INTEGRATION_TEST_USER_ID);
        final String json = """
                {"courtCentreId": "%s", "startDate": "%s", "endDate": "%s", "courtListType": "%s"}
                """.formatted(courtCentreId, startDate, endDate, courtListType);
        return http.exchange(CROWN_PUBLISH_ENDPOINT, HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
    }

    private JsonNode waitForAdditionalCaTHRequest(final String cathListType, final int previousCount) throws Exception {
        final long deadline = System.currentTimeMillis() + CATH_BODY_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            final List<JsonNode> requests = findCaTHRequests(cathListType);
            if (requests.size() > previousCount) {
                return requests.getLast();
            }
            Thread.sleep(WIREMOCK_POLL_MS);
        }
        throw new AssertionError("No new CaTH POST for " + cathListType + " after publish; was " + previousCount);
    }

    /** The CaTH publications WireMock has seen for one list type, found through its admin API. */
    private List<JsonNode> findCaTHRequests(final String cathListType) throws Exception {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        final String criteria = """
                {"method": "POST", "urlPath": "%s", "headers": {"x-list-type": {"equalTo": "%s"}}}
                """.formatted(CATH_PUBLICATION_URL_PATH, cathListType);
        final ResponseEntity<String> found = http.exchange(WIREMOCK_ADMIN_REQUESTS + "/find", HttpMethod.POST,
                new HttpEntity<>(criteria, headers), String.class);
        assertThat(found.getStatusCode().is2xxSuccessful()).isTrue();

        final List<JsonNode> requests = new ArrayList<>();
        objectMapper.readTree(found.getBody()).path("requests").forEach(requests::add);
        return requests;
    }

    private static String header(final JsonNode request, final String name) {
        final Iterator<Map.Entry<String, JsonNode>> headers = request.path("headers").fields();
        while (headers.hasNext()) {
            final Map.Entry<String, JsonNode> entry = headers.next();
            if (entry.getKey().equalsIgnoreCase(name)) {
                final JsonNode value = entry.getValue();
                return value.isArray() ? value.get(0).asText() : value.asText();
            }
        }
        return null;
    }

    private static String body(final JsonNode request) {
        if (request.hasNonNull("body") && !request.get("body").asText().isBlank()) {
            return request.get("body").asText();
        }
        return new String(Base64.getDecoder().decode(request.path("bodyAsBase64").asText()), StandardCharsets.UTF_8);
    }
}
