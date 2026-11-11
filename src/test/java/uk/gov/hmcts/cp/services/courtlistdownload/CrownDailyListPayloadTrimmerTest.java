package uk.gov.hmcts.cp.services.courtlistdownload;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class CrownDailyListPayloadTrimmerTest {

    @Test
    void keepsEveryTemplateFieldAndDropsTheRest() throws IOException {
        JsonObject full = load("courtlistdownload/crown-daily-list-payload.json");
        JsonObject expected = load("courtlistdownload/crown-daily-list-payload-trimmed.json");

        JsonObject trimmed = CrownDailyListPayloadTrimmer.trim(full);

        assertThat(trimmed).isEqualTo(expected);
        assertThat(trimmed.toString().length()).isLessThan(full.toString().length() / 2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"draft", "final", "firm", "online-public"})
    void trimsFullPayloadOfEachCrownListTypeToItsExpectedTemplatePayload(String listType) throws IOException {
        JsonObject full = load("courtlistdownload/templates/crown-" + listType + "-full.json");
        JsonObject expected = load("courtlistdownload/templates/crown-" + listType + "-trimmed.json");

        assertThat(CrownDailyListPayloadTrimmer.trim(full)).isEqualTo(expected);
    }

    @Test
    void keepsArraySizesSoTemplateConditionsAreUnchanged() throws IOException {
        JsonObject trimmed = CrownDailyListPayloadTrimmer.trim(load("courtlistdownload/crown-daily-list-payload.json"));

        var sittings = trimmed.getJsonArray("courtLists").getJsonObject(0).getJsonArray("sittings");
        assertThat(sittings).hasSize(2);
        var hearing = sittings.getJsonObject(0).getJsonArray("hearings").getJsonObject(0);
        var defendants = hearing.getJsonArray("defendants");
        assertThat(defendants).hasSize(2);
        assertThat(defendants.getJsonObject(0).getJsonArray("offences")).hasSize(2);
        assertThat(defendants.getJsonObject(1).getJsonArray("offences")).isEmpty();
        assertThat(sittings.getJsonObject(1).getJsonArray("judiciary")).isEmpty();
    }

    @Test
    void dropsDataNoTemplatePrints() throws IOException {
        String trimmed = CrownDailyListPayloadTrimmer.trim(load("courtlistdownload/crown-daily-list-payload.json")).toString();

        assertThat(trimmed).doesNotContain(
                "\"id\"", "courtCentreId", "courtRoomId", "judicialId", "judicialRoleType", "judiciaryForenames",
                "endTime", "restrictFromCourtList", "linkedCases", "committingCourt", "offenceCode", "welshTitle",
                "welshLabel", "applicant", "respondents", "subject", "sittingNote", "courtCentrePostcode", "isWelsh");
    }

    @Test
    void keepsNonObjectValuesAndUnknownShapesSafely() {
        JsonObject payload = Json.createObjectBuilder()
                .add("courtCentreName", "X")
                .add("documentCounter", 3)
                .add("unknownTopLevel", "dropped")
                .add("courtLists", Json.createArrayBuilder().add("plain-string").addNull())
                .build();

        JsonObject trimmed = CrownDailyListPayloadTrimmer.trim(payload);

        assertThat(trimmed.getString("courtCentreName")).isEqualTo("X");
        assertThat(trimmed.getInt("documentCounter")).isEqualTo(3);
        assertThat(trimmed.containsKey("unknownTopLevel")).isFalse();
        assertThat(trimmed.getJsonArray("courtLists")).hasSize(2);
    }

    private static JsonObject load(String resource) throws IOException {
        try (InputStream in = CrownDailyListPayloadTrimmerTest.class.getClassLoader().getResourceAsStream(resource);
             JsonReader reader = Json.createReader(in)) {
            return reader.readObject();
        }
    }
}
