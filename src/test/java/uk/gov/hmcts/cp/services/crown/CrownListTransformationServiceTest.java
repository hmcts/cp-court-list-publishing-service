package uk.gov.hmcts.cp.services.crown;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.models.CourtCentreData;
import uk.gov.hmcts.cp.services.JsonSchemaValidatorService;
import uk.gov.hmcts.cp.services.sanitization.WafPatternSanitizer;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CrownListTransformationServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-30T16:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate START = LocalDate.parse("2026-11-02");
    private static final LocalDate END = LocalDate.parse("2026-11-08");

    private final JsonSchemaValidatorService schemaValidator = new JsonSchemaValidatorService();

    private CrownListTransformationService service;
    private JsonNode payload;
    private CourtCentreData courtCentre;

    @BeforeEach
    void setUp() throws IOException {
        service = new CrownListTransformationService(List.of(
                new CrownDailyListTransformer(CLOCK),
                new CrownAdvanceListTransformer(CLOCK),
                new CrownFirmListTransformer(CLOCK)),
                new WafPatternSanitizer("..\\,../"));
        try (InputStream in = getClass().getResourceAsStream("/crown/listing-crown-list-payload.json")) {
            payload = MAPPER.readTree(in);
        }
        courtCentre = CourtCentreData.builder()
                .oucodeL3Name("Example Crown Court")
                .courtIdNumeric("100")
                .address1("1 Example Street")
                .address2("Exampleton")
                .postcode("EX1 1AA")
                .phone("01234 567890")
                .build();
    }

    // ---- every document validates against its CaTH schema ------------------------------------------------------

    @Test
    void dailyListValidatesAgainstTheCrownDailyCrownSchema() throws IOException {
        schemaValidator.validate(json(CrownListType.DAILY), CrownListType.DAILY.schema());
    }

    @Test
    void advanceListValidatesAgainstTheCrownAdvanceCrownSchema() throws IOException {
        schemaValidator.validate(json(CrownListType.ADVANCE), CrownListType.ADVANCE.schema());
    }

    @Test
    void firmListValidatesAgainstTheCrownFirmCrownSchema() throws IOException {
        schemaValidator.validate(json(CrownListType.FIRM), CrownListType.FIRM.schema());
    }

    @Test
    void theSchemaCheckRejectsACrownDocumentThatBreaksTheSchema() throws IOException {
        final com.fasterxml.jackson.databind.node.ObjectNode broken = (com.fasterxml.jackson.databind.node.ObjectNode) MAPPER.readTree(json(CrownListType.DAILY));
        ((com.fasterxml.jackson.databind.node.ObjectNode) broken.get("DailyList")).remove("ListHeader");
        final String brokenJson = broken.toString();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> schemaValidator.validate(brokenJson, CrownListType.DAILY.schema()))
                .isInstanceOf(uk.gov.hmcts.cp.services.SchemaValidationException.class)
                .hasMessageContaining("ListHeader");
    }

    @Test
    void everyListTypeStillValidatesWhenTheListingReturnsNoCourtListsAndReferenceDataIsMissing() throws IOException {
        final JsonNode empty = MAPPER.readTree("{}");
        for (final CrownListType type : CrownListType.values()) {
            final String json = service.transform(type, empty, null, START, END);
            schemaValidator.validate(json, type.schema());
            assertThat(MAPPER.readTree(json).path(type.rootElement()).path("CourtLists")).isEmpty();
        }
    }

    @Test
    void everyListTypeValidatesWithoutReferenceDataUsingTheCourtSiteFromTheListing() throws IOException {
        for (final CrownListType type : CrownListType.values()) {
            final JsonNode list = MAPPER.readTree(service.transform(type, payload, null, START, END)).path(type.rootElement());
            schemaValidator.validate(list.toString().isEmpty() ? "" : "{\"" + type.rootElement() + "\":" + list + "}", type.schema());
            assertThat(list.path("CrownCourt").path("CourtHouseCode").asInt()).isEqualTo(101);
            assertThat(list.path("CrownCourt").path("CourtHouseName").asText()).isEqualTo("Example Crown Court");
            assertThat(list.path("CrownCourt").path("CourtHouseAddress").path("Line").get(0).asText()).isEqualTo("1 Example Street");
            assertThat(list.path("CrownCourt").path("CourtHouseAddress").path("PostCode").asText()).isEqualTo("EX1 1AA");
            assertThat(list.path("CrownCourt").has("CourtHouseTelephone")).isFalse();
        }
    }

    @Test
    void textThatTheWebApplicationFirewallRejectsIsRemovedFromEveryTextValue() throws IOException {
        final com.fasterxml.jackson.databind.node.ObjectNode edited = (com.fasterxml.jackson.databind.node.ObjectNode) payload.deepCopy();
        final com.fasterxml.jackson.databind.node.ObjectNode hearing = (com.fasterxml.jackson.databind.node.ObjectNode) edited
                .path("courtLists").get(0).path("sittings").get(0).path("hearings").get(0);
        hearing.put("publicListNote", "See ../etc/notes for details");
        ((com.fasterxml.jackson.databind.node.ObjectNode) edited.path("courtLists").get(0).path("sittings").get(0)).put("sittingNote", "Late ../start");

        final String json = service.transform(CrownListType.DAILY, edited, courtCentre, START, END);

        assertThat(json).doesNotContain("../");
        assertThat(json).contains("See").contains("etc/notes for details").contains("Late").contains("start");
        schemaValidator.validate(json, CrownListType.DAILY.schema());
    }

    // ---- header and court --------------------------------------------------------------------------------------

    @Test
    void headerAndCourtComeFromTheRequestAndReferenceData() throws IOException {
        final JsonNode list = list(CrownListType.DAILY);

        assertThat(list.path("DocumentID").path("DocumentType").asText()).isEqualTo("DL");
        assertThat(list.path("DocumentID").path("UniqueID").asText()).hasSize(36);
        assertThat(list.path("ListHeader").path("StartDate").asText()).isEqualTo("2026-11-02");
        assertThat(list.path("ListHeader").path("EndDate").asText()).isEqualTo("2026-11-08");
        assertThat(list.path("ListHeader").path("PublishedTime").asText()).isEqualTo("2026-10-30T16:00:00Z");
        assertThat(list.path("CrownCourt").path("CourtHouseType").asText()).isEqualTo("Crown Court");
        assertThat(list.path("CrownCourt").path("CourtHouseCode").asInt()).isEqualTo(100);
        assertThat(list.path("CrownCourt").path("CourtHouseTelephone").asText()).isEqualTo("01234 567890");
        assertThat(list.path("CourtLists").get(0).path("CourtHouse").path("CourtHouseCode").asInt()).isEqualTo(101);
        assertThat(list.path("CourtLists").get(0).path("CourtHouse").path("CourtHouseShortName").asText()).isEqualTo("Example");
    }

    // ---- daily list --------------------------------------------------------------------------------------------

    @Test
    void dailySittingsAreOrderedByCourtRoomAndCarryJudiciaryAndNote() throws IOException {
        final JsonNode sittings = list(CrownListType.DAILY).path("CourtLists").get(0).path("Sittings");

        assertThat(roomNumbers(sittings)).containsExactly(0, 1, 2);
        final JsonNode courtTwo = sittings.get(2);
        assertThat(courtTwo.path("SittingNote").asText()).isEqualTo("Court 2 sits late");
        assertThat(courtTwo.path("SittingPriority").asText()).isEqualTo("T");
        assertThat(courtTwo.path("SittingAt").asText()).isEqualTo("10:00:00");
        final JsonNode judge = courtTwo.path("Judiciary").path("Judge");
        assertThat(judge.path("CitizenNameTitle").asText()).isEqualTo("His Honour Judge");
        assertThat(judge.path("CitizenNameForename").get(0).asText()).isEqualTo("Mark J");
        assertThat(judge.path("CitizenNameSurname").asText()).isEqualTo("Example");
        assertThat(judge.path("CitizenNameSuffix").asText()).isEqualTo("KC");
        assertThat(judge.path("CitizenNameRequestedName").asText()).isEqualTo("His Honour Judge Example KC");
        assertThat(courtTwo.path("Judiciary").path("Justice").get(0).path("CitizenNameSurname").asText()).isEqualTo("Justice");
    }

    @Test
    void aSittingWithNoJudiciaryGetsThePlaceholderJudgeBecauseTheSchemaRequiresOne() throws IOException {
        final JsonNode courtOne = list(CrownListType.DAILY).path("CourtLists").get(0).path("Sittings").get(1);

        assertThat(courtOne.path("Judiciary").path("Judge").path("CitizenNameSurname").asText()).isEqualTo("To be allocated");
    }

    @Test
    void dailyHearingsAreOrderedByStartTimeAndRestrictedHearingsAreLeftOut() throws IOException {
        final JsonNode hearings = list(CrownListType.DAILY).path("CourtLists").get(0).path("Sittings").get(2).path("Hearings");

        assertThat(hearings).hasSize(2);
        assertThat(hearings.get(0).path("CaseNumber").asText()).isEqualTo("T20260002");
        assertThat(hearings.get(0).path("HearingSequenceNumber").asInt()).isEqualTo(1);
        assertThat(hearings.get(0).path("TimeMarkingNote").asText()).isEqualTo("10:00 AM");
        assertThat(hearings.get(1).path("CaseNumber").asText()).isEqualTo("T20260001");
        assertThat(hearings.get(1).path("HearingSequenceNumber").asInt()).isEqualTo(2);
        assertThat(hearings.get(1).path("TimeMarkingNote").asText()).isEqualTo("2:30 PM");
        assertThat(hearings.toString()).doesNotContain("T20269999").doesNotContain("Never");
    }

    @Test
    void dailyHearingCarriesCaseProsecutionCommittingCourtAndNote() throws IOException {
        final JsonNode hearing = list(CrownListType.DAILY).path("CourtLists").get(0).path("Sittings").get(2).path("Hearings").get(1);

        assertThat(hearing.path("HearingDetails").path("HearingDescription").asText()).isEqualTo("Trial");
        assertThat(hearing.path("CaseNumberCaTH").asText()).isEqualTo("T20260001");
        assertThat(hearing.path("ListNote").asText()).isEqualTo("Interpreter required");
        assertThat(hearing.path("Prosecution").path("ProsecutingAuthority").asText()).isEqualTo("Crown Prosecution Service");
        assertThat(hearing.path("Prosecution").path("ProsecutingOrganisation").path("OrganisationName").asText()).isEqualTo("Crown Prosecution Service");
        assertThat(hearing.path("CommittingCourt").path("CourtHouseType").asText()).isEqualTo("Magistrates Court");
        assertThat(hearing.path("CommittingCourt").path("CourtHouseCode").asInt()).isEqualTo(200);
        assertThat(hearing.path("CommittingCourt").path("CourtHouseName").asText()).isEqualTo("Example Magistrates Court");
    }

    @Test
    void otherProsecutorsAreMappedToOtherProsecutor() throws IOException {
        final JsonNode hearing = list(CrownListType.DAILY).path("CourtLists").get(0).path("Sittings").get(2).path("Hearings").get(0);

        assertThat(hearing.path("Prosecution").path("ProsecutingAuthority").asText()).isEqualTo("Other Prosecutor");
        assertThat(hearing.has("CommittingCourt")).isFalse();
    }

    @Test
    void defendantPersonalDetailsAgeAndChargesAreMapped() throws IOException {
        final JsonNode defendant = list(CrownListType.DAILY).path("CourtLists").get(0).path("Sittings").get(2)
                .path("Hearings").get(1).path("Defendants").get(0);

        final JsonNode details = defendant.path("PersonalDetails");
        assertThat(details.path("IsMasked").asText()).isEqualTo("no");
        assertThat(details.path("Name").path("CitizenNameSurname").asText()).isEqualTo("Defendant");
        assertThat(details.path("Name").path("CitizenNameRequestedName").asText()).isEqualTo("Test Defendant");
        assertThat(details.path("DateOfBirth").path("BirthDate").asText()).isEqualTo("1990-04-15");
        assertThat(details.path("Age").asInt()).isEqualTo(36);
        assertThat(details.path("Address").path("Line")).hasSize(2);
        assertThat(details.path("Address").path("PostCode").asText()).isEqualTo("SM1 2BB");
        assertThat(details.path("Nationality").asText()).isEqualTo("British");
        assertThat(defendant.path("URN").asText()).isEqualTo("T20260001");
        assertThat(defendant.has("Counsel")).as("the daily list schema has no counsel").isFalse();
        assertThat(defendant.path("Charges")).hasSize(1);
        assertThat(defendant.path("Charges").get(0).path("OffenceStatement").asText()).isEqualTo("Example offence");
        assertThat(defendant.path("Charges").get(0).path("CJSoffenceCode").asText()).isEqualTo("TEST001");
        assertThat(defendant.path("Charges").get(0).path("IndictmentCountNumber").asInt()).isEqualTo(1);
    }

    @Test
    void aRestrictedDefendantIsMaskedWithNoPersonalDetails() throws IOException {
        final JsonNode masked = list(CrownListType.DAILY).path("CourtLists").get(0).path("Sittings").get(2)
                .path("Hearings").get(1).path("Defendants").get(1).path("PersonalDetails");

        assertThat(masked.path("IsMasked").asText()).isEqualTo("yes");
        assertThat(masked.path("MaskedName").asText()).isEqualTo("******");
        assertThat(masked.path("Name").path("CitizenNameSurname").asText()).isEqualTo("******");
        assertThat(masked.toString()).doesNotContain("Hidden").doesNotContain("Person").doesNotContain("1985");
        assertThat(masked.has("DateOfBirth")).isFalse();
        assertThat(masked.has("Address")).isFalse();
    }

    @Test
    void anApplicationHearingUsesTheApplicationReferenceAndListsRespondents() throws IOException {
        final JsonNode hearing = list(CrownListType.DAILY).path("CourtLists").get(0).path("Sittings").get(1).path("Hearings").get(0);

        assertThat(hearing.path("CaseNumber").asText()).isEqualTo("APP00001");
        assertThat(hearing.path("Respondent")).hasSize(1);
        assertThat(hearing.path("Respondent").get(0).asText()).isEqualTo("Test Respondent");
    }

    // ---- advance list ------------------------------------------------------------------------------------------

    @Test
    void advanceListSplitsCasesIntoFixedDateAndWithoutFixedDate() throws IOException {
        final JsonNode courtList = list(CrownListType.ADVANCE).path("CourtLists").get(0);

        final List<String> fixedDates = new ArrayList<>();
        courtList.path("WithFixedDate").forEach(group -> group.path("Fixture").forEach(fixture -> fixedDates.add(fixture.path("FixedDate").asText())));
        assertThat(fixedDates).containsExactlyInAnyOrder("2026-11-02", "2026-11-02", "2026-11-03");

        final List<String> floating = caseNumbers(courtList.path("WithoutFixedDate"));
        assertThat(floating).containsExactlyInAnyOrder("T20260003", "35WX6068590");
    }

    @Test
    void advanceCaseCarriesLinkedCasesCustodyStatusCounselAndOriginatingCourt() throws IOException {
        final JsonNode theCase = findCase(list(CrownListType.ADVANCE).path("CourtLists").get(0).path("WithFixedDate"), "T20260001");

        assertThat(theCase.path("CaseNumberCaTH").asText()).isEqualTo("T20260001");
        assertThat(theCase.path("LinkedCases").get(0).path("CaseNumber").asText()).isEqualTo("LINKEDURN001");
        assertThat(theCase.path("CaseArrivedFrom").path("OriginatingCourt").path("CourtHouseCode").asInt()).isEqualTo(200);
        assertThat(theCase.path("CaseArrivedFrom").path("OriginatingCourt").path("CourtHouseName").asText()).isEqualTo("Example Magistrates Court");
        assertThat(theCase.path("Hearing").get(0).path("HearingDate").asText()).isEqualTo("2026-11-02");
        final JsonNode defendant = theCase.path("Defendants").get(0);
        assertThat(defendant.path("CustodyStatus").asText()).isEqualTo("In custody");
        assertThat(defendant.path("Counsel").get(0).path("Solicitor").get(0).path("Party").path("Organisation").path("OrganisationName").asText())
                .isEqualTo("Example Solicitors LLP");
        assertThat(theCase.path("Prosecution").path("ProsecutingAuthority").asText()).isEqualTo("Crown Prosecution Service");
    }

    @Test
    void advanceListUsesTheWeekStartAsTheHearingDateForCasesWithoutAFixedDate() throws IOException {
        final JsonNode theCase = findCase(list(CrownListType.ADVANCE).path("CourtLists").get(0).path("WithoutFixedDate"), "T20260003");

        assertThat(theCase.path("Hearing").get(0).path("HearingDate").asText()).isEqualTo("2026-11-02");
    }

    // ---- firm list ---------------------------------------------------------------------------------------------

    @Test
    void firmListHasOneCourtListPerSittingDateWithTheDocumentTimestamp() throws IOException {
        final JsonNode list = list(CrownListType.FIRM);

        assertThat(list.path("DocumentID").path("DocumentType").asText()).isEqualTo("FL");
        assertThat(list.path("DocumentID").path("TimeStamp").asText()).isEqualTo("2026-10-30T16:00:00Z");
        final List<String> dates = new ArrayList<>();
        list.path("CourtLists").forEach(courtList -> dates.add(courtList.path("SittingDate").asText()));
        assertThat(dates).containsExactly("2026-11-02", "2026-11-03");
        final JsonNode sitting = list.path("CourtLists").get(0).path("Sittings").get(0);
        assertThat(sitting.path("SittingPriority").asText()).isEqualTo("F");
        assertThat(sitting.path("CourtRoomNumber").asInt()).isEqualTo(2);
    }

    @Test
    void firmHearingCarriesDatesAndDefendantCounsel() throws IOException {
        final JsonNode hearing = list(CrownListType.FIRM).path("CourtLists").get(0).path("Sittings").get(0).path("Hearings").get(1);

        assertThat(hearing.path("HearingDetails").path("HearingDate").asText()).isEqualTo("2026-11-02");
        assertThat(hearing.path("HearingDetails").path("HearingEndDate").asText()).isEqualTo("2026-11-04");
        final JsonNode defendant = hearing.path("Defendants").get(0);
        assertThat(defendant.path("Counsel").get(0).path("Solicitor").get(0).path("Party").path("Organisation").path("OrganisationName").asText())
                .isEqualTo("Example Solicitors LLP");
        assertThat(defendant.has("CustodyStatus")).as("the firm list schema has no custody status").isFalse();
    }

    @Test
    void firmReserveListHoldsWeekCommencingHearingsWhoseCaseNumberTheSchemaAccepts() throws IOException {
        final JsonNode reserveList = list(CrownListType.FIRM).path("ReserveList");

        assertThat(reserveList).hasSize(1);
        assertThat(reserveList.get(0).path("CaseNumber").asText()).isEqualTo("T20260003");
        assertThat(reserveList.get(0).path("CaseNumberCaTH").asText()).isEqualTo("T20260003");
        assertThat(reserveList.get(0).path("Defendants").get(0).path("URN").asText()).isEqualTo("T20260003");
        assertThat(reserveList.toString()).doesNotContain("35WX6068590");
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    private String json(final CrownListType type) {
        return service.transform(type, payload, courtCentre, START, END);
    }

    private JsonNode list(final CrownListType type) throws IOException {
        return MAPPER.readTree(json(type)).path(type.rootElement());
    }

    private static List<Integer> roomNumbers(final JsonNode sittings) {
        final List<Integer> numbers = new ArrayList<>();
        sittings.forEach(sitting -> numbers.add(sitting.path("CourtRoomNumber").asInt()));
        return numbers;
    }

    private static List<String> caseNumbers(final JsonNode groups) {
        final List<String> numbers = new ArrayList<>();
        groups.forEach(group -> group.path("Fixture").forEach(fixture ->
                fixture.path("Cases").forEach(theCase -> numbers.add(theCase.path("CaseNumber").asText()))));
        return numbers;
    }

    private static JsonNode findCase(final JsonNode groups, final String caseNumber) {
        for (final JsonNode group : groups) {
            for (final JsonNode fixture : group.path("Fixture")) {
                for (final JsonNode theCase : fixture.path("Cases")) {
                    if (caseNumber.equals(theCase.path("CaseNumber").asText())) {
                        return theCase;
                    }
                }
            }
        }
        throw new AssertionError("Case " + caseNumber + " not found in " + groups);
    }
}
