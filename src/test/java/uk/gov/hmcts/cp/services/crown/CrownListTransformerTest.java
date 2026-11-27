package uk.gov.hmcts.cp.services.crown;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;
import uk.gov.hmcts.cp.models.crown.CrownListPayload;
import uk.gov.hmcts.cp.models.crown.CrownListPayload.CaseIdentifier;
import uk.gov.hmcts.cp.models.crown.CrownListPayload.CourtList;
import uk.gov.hmcts.cp.models.crown.CrownListPayload.CrestCourtSite;
import uk.gov.hmcts.cp.models.crown.CrownListPayload.Defendant;
import uk.gov.hmcts.cp.models.crown.CrownListPayload.Hearing;
import uk.gov.hmcts.cp.models.crown.CrownListPayload.Judiciary;
import uk.gov.hmcts.cp.models.crown.CrownListPayload.Party;
import uk.gov.hmcts.cp.models.crown.CrownListPayload.Sitting;
import uk.gov.hmcts.cp.services.JsonSchemaValidatorService;
import uk.gov.hmcts.cp.services.PublicationSchema;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CrownListTransformerTest {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperConfig.getObjectMapper();
    private static final UUID COURT_LIST_ID = UUID.fromString("6f0c1f5e-8a8e-4c55-9d8b-0b1d0d6b7a11");
    private static final LocalDate MONDAY = LocalDate.of(2026, 6, 8);
    private static final LocalDate TUESDAY = LocalDate.of(2026, 6, 9);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 6, 14);

    private final CrownListTransformer transformer =
            new CrownListTransformer(Clock.fixed(Instant.parse("2026-06-08T09:30:15.123Z"), ZoneOffset.UTC));
    private final JsonSchemaValidatorService validator = new JsonSchemaValidatorService();

    // ---- schema conformance ----------------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(CrownListType.class)
    void listingExampleBecomesASchemaValidCaTHDocument(CrownListType type) throws IOException {
        CrownListTransformer.Result result = transformer.transform(request(type), listingExample());

        assertThat(result.document().fieldNames()).toIterable().containsExactly(type.rootElement());
        assertThat(result.hearingsListed()).isEqualTo(2);
        assertThatCode(() -> validator.validate(OBJECT_MAPPER.writeValueAsString(result.document()), type.schema()))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
            "CROWN_DAILY_PDDA, crown/cath-sample-daily.json",
            "CROWN_FIRM_PDDA, crown/cath-sample-firm.json",
            "CROWN_ADVANCE_PDDA, crown/cath-sample-advance.json"})
    void cathSamplesAreValidAgainstTheBundledSchemas(PublicationSchema schema, String sample) throws IOException {
        String json = OBJECT_MAPPER.writeValueAsString(readTree(sample));
        assertThatCode(() -> validator.validate(json, schema)).doesNotThrowAnyException();
    }

    // ---- daily list ------------------------------------------------------------------------------------------

    @Test
    void dailyListMapsHeaderCourtSittingAndHearings() throws IOException {
        JsonNode list = transformer.transform(request(CrownListType.DAILY), listingExample()).document().get("DailyList");

        assertThat(list.at("/DocumentID/UniqueID").asText()).isEqualTo(COURT_LIST_ID.toString());
        assertThat(list.at("/DocumentID/DocumentType").asText()).isEqualTo("DL");
        assertThat(list.at("/ListHeader/StartDate").asText()).isEqualTo("2026-06-09");
        assertThat(list.at("/ListHeader/EndDate").asText()).isEqualTo("2026-06-09");
        assertThat(list.at("/ListHeader/Version").asText()).isEqualTo("2");
        assertThat(list.at("/ListHeader/PublishedTime").asText()).isEqualTo("2026-06-08T09:30:15Z");

        // the example is a Welsh court centre, so Welsh names are used where listing has them
        assertThat(list.at("/CrownCourt/CourtHouseCode").asInt()).isEqualTo(100);
        assertThat(list.at("/CrownCourt/CourtHouseName").asText()).isEqualTo("Llys y Goron Caerdydd");
        assertThat(list.at("/CrownCourt/CourtHouseTelephone").asText()).isEqualTo("02920 345678");
        assertThat(list.at("/CrownCourt/CourtHouseAddress/PostCode").asText()).isEqualTo("CF10 1ET");

        JsonNode sitting = list.at("/CourtLists/0/Sittings/0");
        assertThat(sitting.get("CourtRoomNumber").asInt()).isEqualTo(1);
        assertThat(sitting.get("SittingAt").asText()).isEqualTo("10:00:00");
        assertThat(sitting.get("SittingNote").asText()).isEqualTo("Court 1 sits late today");
        assertThat(sitting.at("/Judiciary/Judge/CitizenNameTitle").asText()).isEqualTo("Ei Anrhydedd Barnwr");
        assertThat(sitting.at("/Judiciary/Judge/CitizenNameForename").toString()).isEqualTo("[\"Mark\",\"J\"]");
        assertThat(sitting.at("/Judiciary/Judge/CitizenNameSurname").asText()).isEqualTo("Williams");

        JsonNode trial = sitting.at("/Hearings/0");
        assertThat(trial.get("HearingSequenceNumber").asInt()).isEqualTo(1);
        assertThat(trial.at("/HearingDetails/HearingDescription").asText()).isEqualTo("Treial");
        assertThat(trial.get("TimeMarkingNote").asText()).isEqualTo("10:00");
        assertThat(trial.get("CaseNumber").asText()).isEqualTo("T20260002");
        assertThat(trial.at("/Prosecution/ProsecutingReference").asText()).isEqualTo("CPS002");
        assertThat(trial.at("/Prosecution/ProsecutingAuthority").asText()).isEqualTo("Crown Prosecution Service");
        assertThat(trial.at("/Defendants/0/PersonalDetails/Name/CitizenNameSurname").asText()).isEqualTo("Jones");
        assertThat(trial.at("/Defendants/0/URN").asText()).isEqualTo("T20260002");
        assertThat(trial.at("/Defendants/0/Charges/0/CJSoffenceCode").asText()).isEqualTo("TH68001");
        assertThat(trial.at("/Defendants/0/Counsel").isMissingNode()).isTrue();

        JsonNode application = sitting.at("/Hearings/1");
        assertThat(application.get("CaseNumber").asText()).isEqualTo("APP20260002");
        assertThat(application.at("/Defendants/0/PersonalDetails/Name/CitizenNameSurname").asText()).isEqualTo("Morgan");
        assertThat(application.at("/Respondent/0").asText()).isEqualTo("Crown Prosecution Service");
    }

    @Test
    void publishedTimeKeepsSecondsWhenTheyAreZero() {
        CrownListTransformer onTheMinute =
                new CrownListTransformer(Clock.fixed(Instant.parse("2026-06-08T10:00:00.500Z"), ZoneOffset.UTC));

        JsonNode document = onTheMinute.transform(request(CrownListType.DAILY), payload()).document();

        assertThat(document.at("/DailyList/ListHeader/PublishedTime").asText()).isEqualTo("2026-06-08T10:00:00Z");
    }

    @Test
    void dailyListLeavesOutSittingsWithoutADateOrOnAnotherDay() {
        CrownListPayload payload = payload(
                sitting(null, "Court 1", hearing("T20260010", "2026-06-09T10:00:00")),
                sitting(MONDAY, "Court 2", hearing("T20260011", "2026-06-08T10:00:00")),
                sitting(TUESDAY, "Court 3", hearing("T20260012", "2026-06-09T10:00:00")));

        String daily = transformer.transform(request(CrownListType.DAILY), payload).document().toString();

        assertThat(daily).contains("T20260012").doesNotContain("T20260010", "T20260011");
    }

    // ---- firm list -------------------------------------------------------------------------------------------

    @Test
    void firmListGroupsBySittingDateAndAddsCounsel() throws IOException {
        JsonNode list = transformer.transform(request(CrownListType.FIRM), listingExample()).document().get("FirmList");

        assertThat(list.at("/DocumentID/DocumentType").asText()).isEqualTo("FL");
        assertThat(list.at("/ListHeader/StartDate").asText()).isEqualTo("2026-06-08");
        assertThat(list.at("/ListHeader/EndDate").asText()).isEqualTo("2026-06-14");
        assertThat(list.at("/CourtLists/0/SittingDate").asText()).isEqualTo("2026-06-09");
        JsonNode trial = list.at("/CourtLists/0/Sittings/0/Hearings/0");
        assertThat(trial.at("/HearingDetails/HearingDate").asText()).isEqualTo("2026-06-09");
        assertThat(trial.has("TimeMarkingNote")).isFalse(); // week commencing: no fixed time on the firm list
        assertThat(trial.at("/Defendants/0/Counsel/0/Solicitor/0/Party/Organisation/OrganisationName").asText())
                .isEqualTo("Morgan & Co Solicitors");
    }

    @Test
    void firmListShowsNoTimeForAWeekCommencingSittingWhateverItsHearingsSay() {
        Sitting sitting = new Sitting(TUESDAY, "room", "Court 4", true, null, List.of(),
                List.of(hearing("T20260013", "2026-06-09T10:30:00")));

        JsonNode firmSitting = transformer.transform(request(CrownListType.FIRM), payload(sitting)).document()
                .at("/FirmList/CourtLists/0/Sittings/0");

        assertThat(firmSitting.has("SittingAt")).isFalse();
        assertThat(firmSitting.at("/Hearings/0").has("TimeMarkingNote")).isFalse();
    }

    @Test
    void weekListsLeaveOutSittingsOutsideTheRequestedWeek() {
        CrownListPayload payload = payload(
                sitting(LocalDate.of(2026, 6, 7), "Court 1", hearing("T20260020", null)),
                sitting(LocalDate.of(2026, 6, 11), "Court 2", hearing("T20260021", null)),
                sitting(LocalDate.of(2026, 6, 15), "Court 3", hearing("T20260022", null)));

        for (CrownListType type : List.of(CrownListType.FIRM, CrownListType.WARNED)) {
            String document = transformer.transform(request(type), payload).document().toString();
            assertThat(document).contains("T20260021").doesNotContain("T20260020", "T20260022");
        }
    }

    // ---- warned list -----------------------------------------------------------------------------------------

    @Test
    void warnedListPutsWeekCommencingHearingsWithoutFixedDate() throws IOException {
        JsonNode courtList = transformer.transform(request(CrownListType.WARNED), listingExample()).document()
                .at("/WarnedList/CourtLists/0");

        assertThat(courtList.has("WithFixedDate")).isFalse();
        JsonNode cases = courtList.at("/WithoutFixedDate/0/Fixture/0/Cases");
        assertThat(cases).hasSize(2);
        assertThat(cases.at("/0/CaseNumber").asText()).isEqualTo("T20260002");
        assertThat(cases.at("/0/LinkedCases/0/CaseNumber").asText()).isEqualTo("LINKEDURN001");
        assertThat(cases.at("/0/Prosecution/ProsecutingReference").isMissingNode()).isTrue();
    }

    @Test
    void warnedListPutsTimedHearingsUnderTheirFixedDate() {
        JsonNode courtList = transformer.transform(request(CrownListType.WARNED),
                        payload(sitting(LocalDate.of(2026, 6, 10), "Court 4", hearing("T20260030", "2026-06-10T10:30:00"))))
                .document().at("/WarnedList/CourtLists/0");

        JsonNode fixture = courtList.at("/WithFixedDate/0/Fixture/0");
        assertThat(fixture.get("FixedDate").asText()).isEqualTo("2026-06-10");
        assertThat(fixture.at("/Cases/0/Hearing/0/HearingDate").asText()).isEqualTo("2026-06-10");
        assertThat(courtList.has("WithoutFixedDate")).isFalse();
    }

    // ---- restricted and incomplete data ----------------------------------------------------------------------

    @Test
    void restrictedDataIsNeverPublished() {
        Hearing restrictedHearing = new Hearing("2026-06-09T09:00:00", null, true, false, null,
                new CaseIdentifier("T20260040", null), null, List.of(defendant("Ann", "Smith", false)), null, null, null, null, null);
        Hearing allDefendantsRestricted = new Hearing("2026-06-09T10:00:00", null, false, false, "note",
                new CaseIdentifier("T20260041", "CPS"), null,
                List.of(defendant("Bob", "One", true), defendant("Cat", "Two", true)), null, null, null, null, null);
        Hearing restrictedApplicant = new Hearing("2026-06-09T11:00:00", null, false, false, null, null, null, List.of(),
                null, "APP20260042", new Party("Dee", "Three", true), List.of(), null);
        Hearing mixed = new Hearing("2026-06-09T12:00:00", null, false, false, null,
                new CaseIdentifier("T20260043", null), null,
                List.of(defendant("Hidden", "Person", true), defendant("Shown", "Person", false)), null, null, null, null, null);
        CrownListPayload payload = payload(sitting(TUESDAY, "Court 2", restrictedHearing, allDefendantsRestricted, restrictedApplicant, mixed));

        for (CrownListType type : CrownListType.values()) {
            String document = transformer.transform(request(type), payload).document().toString();
            assertThat(document).doesNotContain("T20260040", "T20260041", "APP20260042", "Hidden").contains("T20260043", "Shown");
        }
    }

    @Test
    void partiesWithoutANameAndHearingsWithoutACaseNumberAreLeftOut() {
        Hearing nameless = new Hearing("2026-06-09T10:00:00", null, false, false, null,
                new CaseIdentifier("T20260050", null), null,
                List.of(defendant(null, " ", false), defendant("Ann", "Smith", false)), null, null, null, null, null);
        Hearing noCaseNumber = new Hearing("2026-06-09T11:00:00", null, false, false, null, null, null,
                List.of(defendant("Bob", "Brown", false)), null, null, null, null, null);

        CrownListTransformer.Result result = transformer.transform(request(CrownListType.DAILY),
                payload(sitting(TUESDAY, "Court 1", nameless, noCaseNumber)));

        JsonNode hearings = result.document().at("/DailyList/CourtLists/0/Sittings/0/Hearings");
        assertThat(hearings).hasSize(1);
        assertThat(hearings.at("/0/Defendants")).hasSize(1);
        assertThat(result.hearingsInPayload()).isEqualTo(2);
        assertThat(result.hearingsListed()).isEqualTo(1);
    }

    // ---- judiciary -------------------------------------------------------------------------------------------

    @Test
    void benchChairmanIsTheJudgeWhateverTheOrder() {
        Sitting sitting = new Sitting(TUESDAY, "room", "Court 1", false, null,
                List.of(judge("Wing", false, false), judge("Presiding", true, false)),
                List.of(hearing("T20260060", "2026-06-09T10:00:00")));

        JsonNode judiciary = transformer.transform(request(CrownListType.DAILY), payload(sitting)).document()
                .at("/DailyList/CourtLists/0/Sittings/0/Judiciary");

        assertThat(judiciary.at("/Judge/CitizenNameSurname").asText()).isEqualTo("Presiding");
        assertThat(judiciary.at("/Justice/0/CitizenNameSurname").asText()).isEqualTo("Wing");
    }

    @Test
    void firstJudgeWhoIsNotADeputyIsTheJudgeWhenNoneChairsTheBench() {
        Sitting sitting = new Sitting(TUESDAY, "room", "Court 1", false, null,
                List.of(judge("Deputy", false, true), judge("Senior", false, false)),
                List.of(hearing("T20260061", "2026-06-09T10:00:00")));

        JsonNode judiciary = transformer.transform(request(CrownListType.DAILY), payload(sitting)).document()
                .at("/DailyList/CourtLists/0/Sittings/0/Judiciary");

        assertThat(judiciary.at("/Judge/CitizenNameSurname").asText()).isEqualTo("Senior");
        assertThat(judiciary.at("/Justice/0/CitizenNameSurname").asText()).isEqualTo("Deputy");
    }

    @Test
    void sittingWithoutJudiciaryGetsAPlaceholderJudgeAndStaysSchemaValid() throws IOException {
        JsonNode document = transformer.transform(request(CrownListType.DAILY),
                payload(sitting(TUESDAY, "Court 2", hearing("T20260062", "2026-06-09T10:00:00")))).document();

        assertThat(document.at("/DailyList/CourtLists/0/Sittings/0/Judiciary/Judge/CitizenNameSurname").asText())
                .isEqualTo(CrownListTransformer.JUDGE_NOT_ALLOCATED);
        assertThatCode(() -> validator.validate(OBJECT_MAPPER.writeValueAsString(document), PublicationSchema.CROWN_DAILY_PDDA))
                .doesNotThrowAnyException();
    }

    // ---- small helpers ---------------------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource(value = {"Court Room 12, 12", "Court 3A, 3", "Annexe, 0", "NULL, 0"}, nullValues = "NULL")
    void courtRoomNumberIsTheLastNumberInTheName(String name, int expected) {
        assertThat(CrownListTransformer.courtRoomNumber(name)).isEqualTo(expected);
    }

    @Test
    void startTimeIsReadAsUkTime() {
        assertThat(CrownListTransformer.startTime("2026-06-09T10:00:00")).contains(LocalTime.of(10, 0));
        assertThat(CrownListTransformer.startTime("2026-06-09T09:00:00Z")).contains(LocalTime.of(10, 0));
        assertThat(CrownListTransformer.startTime("not a time")).isEmpty();
    }

    // ---- fixtures --------------------------------------------------------------------------------------------

    private static CrownPublishRequest request(CrownListType type) {
        return type.weekCommencing()
                ? new CrownPublishRequest(COURT_LIST_ID, UUID.randomUUID(), type, MONDAY, SUNDAY, "user-id", "325", "02920 345678")
                : new CrownPublishRequest(COURT_LIST_ID, UUID.randomUUID(), type, TUESDAY, TUESDAY, "user-id", "325", "02920 345678");
    }

    static CrownListPayload listingExample() throws IOException {
        return OBJECT_MAPPER.treeToValue(readTree("crown/listing-crown-list-payload.json"), CrownListPayload.class);
    }

    private static JsonNode readTree(String resource) throws IOException {
        try (InputStream in = CrownListTransformerTest.class.getClassLoader().getResourceAsStream(resource)) {
            return OBJECT_MAPPER.readTree(in);
        }
    }

    private static CrownListPayload payload(Sitting... sittings) {
        CrestCourtSite site = new CrestCourtSite("401", "401", "Leeds Crown Court", null, "Leeds", null, "CROWN",
                "1 Oxford Row", "Leeds", null, null, null, "LS1 3BG", null, null, null, null, null);
        return new CrownListPayload(1, false, "Leeds Crown Court", null,
                sittings.length == 0 ? List.of() : List.of(new CourtList(site, List.of(sittings))));
    }

    private static Sitting sitting(LocalDate date, String room, Hearing... hearings) {
        return new Sitting(date, "room", room, false, null, List.of(), List.of(hearings));
    }

    private static Hearing hearing(String caseReference, String startTime) {
        return new Hearing(startTime, null, false, false, null, new CaseIdentifier(caseReference, null), null,
                List.of(defendant("Ann", "Smith", false)), null, null, null, null, null);
    }

    private static Defendant defendant(String firstName, String lastName, boolean restricted) {
        return new Defendant(firstName, lastName, restricted, null, List.of());
    }

    private static Judiciary judge(String surname, boolean benchChairman, boolean deputy) {
        return new Judiciary(benchChairman, deputy, "Judge " + surname, null, "HHJ", null, null, surname, null, null);
    }
}
