package uk.gov.hmcts.cp.services.sjp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.api.sjp.SjpListPayload;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SjpToCathPayloadTransformerTest {

    private SjpToCathPayloadTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new SjpToCathPayloadTransformer();
    }

    @Test
    void transform_producesDocumentAndCourtLists() throws Exception {
        SjpListPayload payload = new SjpListPayload(
                "2016-01-01 00:00:00",
                List.of(
                        Map.of(
                                "caseUrn", "TFL901845675",
                                "defendantName", "D LastName",
                                "firstName", "David",
                                "lastName", "LastName",
                                "prosecutorName", "TFL",
                                "postcode", "W1T 1JY",
                                "sjpOffences", List.of(Map.of("title", "title1", "wording", "wording1"))
                        )
                )
        );

        String json = transformer.transform(payload, "SJP Public list", false);

        JsonNode root = new ObjectMapper().readTree(json);
        assertThat(root.has("document")).isTrue();
        assertThat(root.get("document").get("documentName").asText()).isEqualTo("SJP Public list");
        assertThat(root.get("document").get("publicationDate").asText()).isEqualTo("2016-01-01 00:00:00");
        assertThat(root.get("document").get("version").asText()).isEqualTo("1.0");
        assertThat(root.has("courtLists")).isTrue();
        assertThat(root.get("courtLists").isArray()).isTrue();
        assertThat(root.get("courtLists").size()).isEqualTo(1);
        JsonNode courtList = root.get("courtLists").get(0);
        assertThat(courtList.has("courtHouse")).isTrue();
        assertThat(courtList.get("courtHouse").has("courtRoom")).isTrue();
        JsonNode hearings = courtList.get("courtHouse").get("courtRoom").get(0)
                .get("session").get(0).get("sittings").get(0).get("hearing");
        assertThat(hearings.size()).isEqualTo(1);
        assertThat(hearings.get(0).has("party")).isTrue();
        assertThat(hearings.get(0).has("offence")).isTrue();
        assertThat(hearings.get(0).has("case")).isTrue();
    }

    @Test
    void transform_pressListIncludesReportingRestrictionOnOffence() throws Exception {
        SjpListPayload payload = new SjpListPayload(
                "2016-01-01 00:00:00",
                List.of(
                        Map.of(
                                "caseUrn", "C1",
                                "defendantName", "Def",
                                "sjpOffences", List.of(
                                        Map.of("title", "T", "wording", "W", "reportingRestriction", true)
                                )
                        )
                )
        );

        String json = transformer.transform(payload, "SJP Press list", true);

        JsonNode root = new ObjectMapper().readTree(json);
        JsonNode offence = root.get("courtLists").get(0).get("courtHouse").get("courtRoom").get(0)
                .get("session").get(0).get("sittings").get(0).get("hearing").get(0)
                .get("offence").get(0);
        assertThat(offence.get("reportingRestriction").asBoolean()).isTrue();
    }
}
