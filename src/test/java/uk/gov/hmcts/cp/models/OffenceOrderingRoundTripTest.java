package uk.gov.hmcts.cp.models;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;
import uk.gov.hmcts.cp.taskmanager.domain.converter.JsonObjectConverter;

/**
 * LPT-2677: the court list PDF numbers each offence with its orderIndex, the number Case at a glance
 * and Manage hearing show. Progression's court list data carries orderIndex and count; the publish
 * task reads it into {@link CourtListPayload} and writes it back out for the document generator, so
 * both fields must survive that round trip.
 */
class OffenceOrderingRoundTripTest {

    private static final String PAYLOAD = """
            {
              "templateName": "BenchAndStandardCourtList",
              "hearingDates": [{
                "hearingDate": "2026-11-30",
                "courtRooms": [{
                  "courtRoomName": "Courtroom 01",
                  "timeslots": [{
                    "hearings": [{
                      "id": "hearing-1",
                      "defendants": [{
                        "offences": [
                          {"id": "offence-1", "offenceCode": "RR84113", "orderIndex": 1, "count": 0},
                          {"id": "offence-3", "offenceCode": "OS23002", "orderIndex": 3, "count": 2},
                          {"id": "offence-unnumbered", "offenceCode": "TH68001"}
                        ]
                      }],
                      "applicationOffences": [
                        {"id": "application-offence", "orderIndex": 5, "count": 4}
                      ],
                      "courtApplication": {
                        "applicant": {
                          "offences": [{"id": "applicant-offence", "orderIndex": 2, "count": 1}]
                        }
                      }
                    }]
                  }]
                }]
              }]
            }
            """;

    private final JsonObjectConverter converter = new JsonObjectConverter();

    @Test
    void defendantOffencesKeepOrderIndexAndCountForThePdf() throws JsonProcessingException {
        final JsonObject hearing = firstHearing(roundTrip());
        final var offences = hearing.getJsonArray("defendants").getJsonObject(0).getJsonArray("offences");

        assertThat(offences.getJsonObject(0).getInt("orderIndex")).isEqualTo(1);
        assertThat(offences.getJsonObject(0).getInt("count")).isZero();
        assertThat(offences.getJsonObject(1).getInt("orderIndex")).isEqualTo(3);
        assertThat(offences.getJsonObject(1).getInt("count")).isEqualTo(2);
    }

    @Test
    void offenceWithoutOrderIndexOrCountOmitsThemSoTheTemplateFallsBackToPosition() throws JsonProcessingException {
        final JsonObject hearing = firstHearing(roundTrip());
        final JsonObject unnumbered = hearing.getJsonArray("defendants").getJsonObject(0)
                .getJsonArray("offences").getJsonObject(2);

        assertThat(unnumbered.containsKey("orderIndex")).isFalse();
        assertThat(unnumbered.containsKey("count")).isFalse();
    }

    @Test
    void applicationOffencesKeepOrderIndexAndCountForThePdf() throws JsonProcessingException {
        final JsonObject hearing = firstHearing(roundTrip());

        final JsonObject applicationOffence = hearing.getJsonArray("applicationOffences").getJsonObject(0);
        assertThat(applicationOffence.getInt("orderIndex")).isEqualTo(5);
        assertThat(applicationOffence.getInt("count")).isEqualTo(4);

        final JsonObject applicantOffence = hearing.getJsonObject("courtApplication").getJsonObject("applicant")
                .getJsonArray("offences").getJsonObject(0);
        assertThat(applicantOffence.getInt("orderIndex")).isEqualTo(2);
        assertThat(applicantOffence.getInt("count")).isEqualTo(1);
    }

    /** Same path as the publish task: CourtListDataService reads the payload, CourtListPdfHelper writes it out. */
    private JsonObject roundTrip() throws JsonProcessingException {
        final CourtListPayload payload = ObjectMapperConfig.getObjectMapper().readValue(PAYLOAD, CourtListPayload.class);
        return converter.convertFromObject(payload);
    }

    private static JsonObject firstHearing(final JsonObject payload) {
        return payload.getJsonArray("hearingDates").getJsonObject(0)
                .getJsonArray("courtRooms").getJsonObject(0)
                .getJsonArray("timeslots").getJsonObject(0)
                .getJsonArray("hearings").getJsonObject(0);
    }
}
