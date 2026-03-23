package uk.gov.hmcts.cp.services.sjp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.domain.sjp.SjpListPayload;
import uk.gov.hmcts.cp.domain.sjp.cath.PubhubMaster;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SjpToCathPayloadTransformerTest {

    private SjpToCathPayloadTransformer transformer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        transformer = new SjpToCathPayloadTransformer();
        objectMapper = new ObjectMapper();
    }

    @Test
    void buildPubhubMaster_returnsStronglyTypedHierarchy() {
        SjpListPayload payload = new SjpListPayload(
                "2025-03-09T10:00:00",
                List.of(
                        Map.<String, Object>of(
                                "caseUrn", "case-1",
                                "defendantName", "Defendant One",
                                "firstName", "Defendant",
                                "lastName", "One",
                                "prosecutorName", "CPS",
                                "sjpOffences", List.of(
                                        Map.of("title", "Offence 1", "wording", "Wording 1")
                                )
                        )
                )
        );

        PubhubMaster master = transformer.buildPubhubMaster(payload, SjpDocumentType.SJP_PUBLIC_LIST.getValue());

        assertThat(master.getDocument()).isNotNull();
        assertThat(master.getDocument().getDocumentName()).isEqualTo("SJP Public list");
        assertThat(master.getDocument().getPublicationDate()).isEqualTo("2025-03-09T10:00:00");
        assertThat(master.getDocument().getVersion()).isEqualTo("1.0");

        assertThat(master.getCourtLists()).hasSize(1);
        assertThat(master.getCourtLists().get(0).getCourtHouse()).isNotNull();
        assertThat(master.getCourtLists().get(0).getCourtHouse().getCourtRoom()).hasSize(1);

        var courtRoom = master.getCourtLists().get(0).getCourtHouse().getCourtRoom().get(0);
        assertThat(courtRoom.getSession()).hasSize(1);
        assertThat(courtRoom.getSession().get(0).getSittings()).hasSize(1);

        var sittings = courtRoom.getSession().get(0).getSittings().get(0);
        assertThat(sittings.getHearing()).hasSize(1);

        var hearing = sittings.getHearing().get(0);
        assertThat(hearing.getParty()).hasSize(2); // prosecutor + defendant
        assertThat(hearing.getOffence()).hasSize(1);
        assertThat(hearing.getOffence().get(0).getOffenceTitle()).isEqualTo("Offence 1");
        assertThat(hearing.getCases()).hasSize(1);
        assertThat(hearing.getCases().get(0).getCaseUrn()).isEqualTo("case-1");
    }

    @Test
    void transform_producesValidJsonWithExpectedStructure() throws Exception {
        SjpListPayload payload = new SjpListPayload(
                "2025-03-09T10:00:00",
                List.of(Map.<String, Object>of("caseUrn", "urn-1", "defendantName", "Name"))
        );

        String json = transformer.transform(payload, SjpDocumentType.SJP_PRESS_LIST.getValue());

        JsonNode root = objectMapper.readTree(json);
        assertThat(root.has("document")).isTrue();
        assertThat(root.get("document").get("documentName").asText()).isEqualTo("SJP Press list");
        assertThat(root.get("document").get("publicationDate").asText()).isEqualTo("2025-03-09T10:00:00");
        assertThat(root.has("courtLists")).isTrue();
        assertThat(root.get("courtLists").isArray()).isTrue();
        assertThat(root.get("courtLists").get(0).has("courtHouse")).isTrue();
        assertThat(root.get("courtLists").get(0).get("courtHouse").has("courtRoom")).isTrue();
    }

    @Test
    void buildPubhubMaster_emptyReadyCases_buildsEmptyCourtRoom() {
        SjpListPayload payload = new SjpListPayload("2025-03-09T10:00:00", List.of());

        PubhubMaster master = transformer.buildPubhubMaster(payload, SjpDocumentType.SJP_PUBLIC_LIST.getValue());

        assertThat(master.getCourtLists()).hasSize(1);
        assertThat(master.getCourtLists().get(0).getCourtHouse().getCourtRoom()).hasSize(1);
        var sittings = master.getCourtLists().get(0).getCourtHouse().getCourtRoom().get(0)
                .getSession().get(0).getSittings().get(0);
        assertThat(sittings.getHearing()).isEmpty();
    }
}
