package uk.gov.hmcts.cp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import uk.gov.hmcts.cp.models.*;
import uk.gov.hmcts.cp.models.transformed.CourtListDocument;
import uk.gov.hmcts.cp.models.transformed.schema.*;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
class StandardCourtListTransformationServiceTest {

    private StandardCourtListTransformationService transformationService;
    private final ObjectMapper objectMapper = ObjectMapperConfig.getObjectMapper();
    private CourtListPayload payload;

    @BeforeEach
    void setUp() throws Exception {
        // Create transformation service
        transformationService = new StandardCourtListTransformationService();
        
        payload = loadPayloadFromStubData("stubdata/court-list-payload-standard.json");
    }

    @Test
    void transform_shouldTransformAllFieldsCorrectly() throws Exception {
        // When
        CourtListDocument document = transformationService.transform(payload);

        // Then - Verify complete document structure
        assertThat(document).isNotNull();
        assertThat(document.getDocument()).isNotNull();
        
        // Verify DocumentSchema with publicationDate
        DocumentSchema documentSchema = document.getDocument();
        assertThat(documentSchema).isNotNull();
        assertThat(documentSchema.getPublicationDate()).isNotNull();
        // Verify publicationDate is in ISO 8601 format
        assertThat(documentSchema.getPublicationDate()).matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}([.]\\d{1,3})?Z$");
        
        // Verify Venue
        Venue venue = document.getVenue();
        assertThat(venue).isNotNull();
        assertThat(venue.getVenueAddress()).isNotNull();
        AddressSchema venueAddress = venue.getVenueAddress();
        assertThat(venueAddress.getLine()).isNotEmpty();
        
        // Verify CourtLists
        List<CourtList> courtLists = document.getCourtLists();
        assertThat(courtLists).isNotNull();
        assertThat(courtLists).isNotEmpty();
        
        // Verify first CourtList
        CourtList courtList = courtLists.getFirst();
        assertThat(courtList).isNotNull();
        assertThat(courtList.getCourtHouse()).isNotNull();
        
        // Verify CourtHouse
        CourtHouse courtHouse = courtList.getCourtHouse();
        assertThat(courtHouse.getCourtHouseName()).isEqualTo("Croydon Crown Court");
        assertThat(courtHouse.getLja()).isEqualTo("Croydon Crown Court");
        assertThat(courtHouse.getCourtRoom()).isNotEmpty();
        
        // Verify first CourtRoom
        CourtRoomSchema courtRoom = courtHouse.getCourtRoom().getFirst();
        assertThat(courtRoom).isNotNull();
        assertThat(courtRoom.getCourtRoomName()).isEqualTo("Courtroom 01");
        assertThat(courtRoom.getSession()).isNotEmpty();
        
        // Verify first Session
        SessionSchema session = courtRoom.getSession().getFirst();
        assertThat(session).isNotNull();
        assertThat(session.getSittings()).isNotEmpty();
        
        // Verify first Sitting
        Sitting sitting = session.getSittings().getFirst();
        assertThat(sitting).isNotNull();
        assertThat(sitting.getSittingStart()).isNotNull();
        // Verify sittingStart is in ISO 8601 format
        assertThat(sitting.getSittingStart()).matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}([.]\\d{1,3})?Z$");
        assertThat(sitting.getHearing()).isNotEmpty();
        
        // Verify first Hearing
        HearingSchema hearing = sitting.getHearing().getFirst();
        assertThat(hearing).isNotNull();
        assertThat(hearing.getHearingType()).isEqualTo("First hearing");
        assertThat(hearing.getCaseList()).isNotEmpty();
        // Schema: channel and application are arrays (empty when no source data)
        assertThat(hearing.getChannel()).isNotNull();
        assertThat(hearing.getChannel()).isEmpty();
        assertThat(hearing.getApplication()).isNotNull();
        assertThat(hearing.getApplication()).isEmpty();
        
        // Verify first Case
        CaseSchema caseObj = hearing.getCaseList().getFirst();
        assertThat(caseObj).isNotNull();
        assertThat(caseObj.getCaseUrn()).isEqualTo("38GD6504026"); // From payload
        assertThat(caseObj.getParty()).isNotEmpty();
        
        // Verify first Party (DEFENDANT)
        Party party = caseObj.getParty().getFirst();
        assertThat(party).isNotNull();
        assertThat(party.getPartyRole()).isEqualTo("DEFENDANT");
        // Stub has no court application, so subject is false
        assertThat(party.getSubject()).isFalse();
        assertThat(party.getIndividualDetails()).isNotNull();
        
        // Verify IndividualDetails
        IndividualDetails individualDetails = party.getIndividualDetails();
        assertThat(individualDetails.getIndividualForenames()).isEqualTo("Alysson"); // From payload
        assertThat(individualDetails.getIndividualSurname()).isEqualTo("Cummings"); // From payload
        assertThat(individualDetails.getDateOfBirth()).isEqualTo("1981-02-21"); // Converted from "21 Feb 1981" to ISO date (yyyy-MM-dd) per schema
        assertThat(individualDetails.getAge()).isEqualTo(44); // Converted from "62"
        
        // Verify Address transformation
        AddressSchema address = individualDetails.getAddress();
        assertThat(address).isNotNull();
        assertThat(address.getLine()).isNotEmpty();
        assertThat(address.getLine().getFirst()).isEqualTo("Address line 1"); // From payload address1
        assertThat(address.getLine().get(1)).isEqualTo("Address line 2"); // From payload address2
        assertThat(address.getLine().get(2)).isEqualTo("Address line 3"); // From payload address3

        // Verify Offences transformation
        List<OffenceSchema> offences = party.getOffence();
        assertThat(offences).isNotNull();
        assertThat(offences).isNotEmpty();

        // Verify first Offence (from stub: id, title, wording)
        OffenceSchema offence = offences.getFirst();
        assertThat(offence).isNotNull();
        assertThat(offence.getOffenceCode()).isEqualTo("OFFCODE112"); // From offence id
        assertThat(offence.getOffenceTitle()).isEqualTo("Use a television set without a licence"); // From payload
        assertThat(offence.getOffenceWording()).contains("television"); // From payload

        // Verify second Party (PROSECUTING_AUTHORITY) from prosecutorType
        if (caseObj.getParty().size() > 1) {
            Party prosecutorParty = caseObj.getParty().get(1);
            assertThat(prosecutorParty).isNotNull();
            assertThat(prosecutorParty.getPartyRole()).isEqualTo("PROSECUTING_AUTHORITY");
            assertThat(prosecutorParty.getOrganisationDetails()).isNotNull();
            assertThat(prosecutorParty.getOrganisationDetails().getOrganisationName()).isEqualTo("CITYPF"); // From prosecutorType in stub
        }
    }

    @Test
    void transform_shouldSetSubjectTrueWhenCourtApplicationSubjectMatchesDefendant() {
        CourtApplicationParty subjectParty = CourtApplicationParty.builder()
                .id("3eebde5d-f238-486a-b181-046b3dd9be93")
                .build();
        Hearing firstHearing = payload.getHearingDates().getFirst().getCourtRooms().getFirst()
                .getTimeslots().getFirst().getHearings().getFirst();
        firstHearing.setCourtApplication(CourtApplication.builder()
                .subject(subjectParty)
                .build());

        CourtListDocument document = transformationService.transform(payload);

        CaseSchema caseObj = document.getCourtLists().getFirst().getCourtHouse().getCourtRoom().getFirst()
                .getSession().getFirst().getSittings().getFirst().getHearing().getFirst().getCaseList().getFirst();

        Party defendantParty = caseObj.getParty().getFirst();
        assertThat(defendantParty.getPartyRole()).isEqualTo("DEFENDANT");
        assertThat(defendantParty.getSubject()).isTrue();
    }

    @Test
    void transform_shouldSetSubjectFalseWhenCourtApplicationHasNoMatchingSubject() {
        CourtApplicationParty otherSubject = CourtApplicationParty.builder()
                .id("different-id-not-matching-defendant")
                .build();
        Hearing firstHearing = payload.getHearingDates().getFirst().getCourtRooms().getFirst()
                .getTimeslots().getFirst().getHearings().getFirst();
        firstHearing.setCourtApplication(CourtApplication.builder()
                .subject(otherSubject)
                .build());

        CourtListDocument document = transformationService.transform(payload);

        CaseSchema caseObj = document.getCourtLists().getFirst().getCourtHouse().getCourtRoom().getFirst()
                .getSession().getFirst().getSittings().getFirst().getHearing().getFirst().getCaseList().getFirst();

        assertThat(caseObj.getParty().getFirst().getSubject()).isFalse();
    }

    @Test
    void transform_shouldHandleMultipleHearingDates() throws Exception {
        // Given
        payload = loadPayloadFromStubData("stubdata/court-list-payload-multiple-dates.json");

        // When
        CourtListDocument document = transformationService.transform(payload);

        // Then
        assertThat(document).isNotNull();
        List<CourtList> courtLists = document.getCourtLists();
        assertThat(courtLists).isNotEmpty();
        
        CourtHouse courtHouse = courtLists.getFirst().getCourtHouse();
        List<CourtRoomSchema> courtRooms = courtHouse.getCourtRoom();
        assertThat(courtRooms.size()).isGreaterThanOrEqualTo(2); // Multiple court rooms
        
        // Verify first court room
        CourtRoomSchema courtRoom1 = courtRooms.getFirst();
        assertThat(courtRoom1.getCourtRoomName()).isEqualTo("Courtroom 01");
        
        // Verify second court room if exists
        if (courtRooms.size() > 1) {
            CourtRoomSchema courtRoom2 = courtRooms.get(1);
            assertThat(courtRoom2.getCourtRoomName()).isEqualTo("Courtroom 02");
        }
    }

    @Test
    void transform_shouldHandleEmptyHearingDates() throws Exception {
        // Given
        payload = loadPayloadFromStubData("stubdata/court-list-payload-empty-hearings.json");

        // When
        CourtListDocument document = transformationService.transform(payload);

        // Then
        assertThat(document).isNotNull();
        assertThat(document.getDocument()).isNotNull();
        assertThat(document.getVenue()).isNotNull();
        
        // CourtLists should exist but may be empty or have empty court rooms
        List<CourtList> courtLists = document.getCourtLists();
        assertThat(courtLists).isNotNull();
        // Either empty list or list with court house but no court rooms
        if (!courtLists.isEmpty()) {
            CourtHouse courtHouse = courtLists.getFirst().getCourtHouse();
            assertThat(courtHouse.getCourtRoom()).isEmpty();
        }
    }

    @Test
    void transform_shouldIncludeJudiciary() throws Exception {
        // When
        CourtListDocument document = transformationService.transform(payload);

        // Then
        assertThat(document).isNotNull();
        List<CourtList> courtLists = document.getCourtLists();
        assertThat(courtLists).isNotEmpty();
        
        CourtHouse courtHouse = courtLists.getFirst().getCourtHouse();
        if (!courtHouse.getCourtRoom().isEmpty()) {
            CourtRoomSchema courtRoom = courtHouse.getCourtRoom().getFirst();
            if (!courtRoom.getSession().isEmpty()) {
                SessionSchema session = courtRoom.getSession().getFirst();
                // Judiciary may be empty if not provided in payload
                List<Judiciary> judiciary = session.getJudiciary();
                assertThat(judiciary).isNotNull();
            }
        }
    }

    @Test
    void transform_shouldIncludeVenueAddress() throws Exception {
        // When
        CourtListDocument document = transformationService.transform(payload);

        // Then
        assertThat(document).isNotNull();
        Venue venue = document.getVenue();
        assertThat(venue).isNotNull();
        AddressSchema venueAddress = venue.getVenueAddress();
        assertThat(venueAddress).isNotNull();
        assertThat(venueAddress.getLine()).isNotNull();
    }

    @Test
    void transform_shouldSetReportingRestrictionFromDefendantReportingRestrictionsArray() throws Exception {
        // Given - defendant with reportingRestrictions array
        Hearing hearing = payload.getHearingDates().getFirst().getCourtRooms().getFirst()
                .getTimeslots().getFirst().getHearings().getFirst();
        Defendant defendant = hearing.getDefendants().getFirst();
        defendant.setReportingRestrictions(List.of(
                ReportingRestriction.builder().label("Section 49 of the Children and Young Persons Act 1933 applies").build()
        ));

        // When
        CourtListDocument document = transformationService.transform(payload);

        // Then - case level
        CaseSchema caseObj = document.getCourtLists().getFirst().getCourtHouse().getCourtRoom().getFirst()
                .getSession().getFirst().getSittings().getFirst().getHearing().getFirst().getCaseList().getFirst();
        assertThat(caseObj.getReportingRestriction()).isTrue();
        assertThat(caseObj.getReportingRestrictionDetails())
                .containsExactly("Section 49 of the Children and Young Persons Act 1933 applies");

        // Then - offence level
        List<OffenceSchema> offences = caseObj.getParty().getFirst().getOffence();
        assertThat(offences).isNotEmpty();
        OffenceSchema offence = offences.getFirst();
        assertThat(offence.getReportingRestriction()).isTrue();
        assertThat(offence.getReportingRestrictionDetails())
                .containsExactly("Section 49 of the Children and Young Persons Act 1933 applies");
    }

    @Test
    void transform_shouldSetNoReportingRestrictionWhenDefendantHasNoReportingRestrictions() throws Exception {
        // Given - stub has reportingRestrictions: [] on defendant; ensure no restrictions
        Hearing hearing = payload.getHearingDates().getFirst().getCourtRooms().getFirst()
                .getTimeslots().getFirst().getHearings().getFirst();
        hearing.getDefendants().getFirst().setReportingRestrictions(Collections.emptyList());

        // When
        CourtListDocument document = transformationService.transform(payload);

        // Then - case level
        CaseSchema caseObj = document.getCourtLists().getFirst().getCourtHouse().getCourtRoom().getFirst()
                .getSession().getFirst().getSittings().getFirst().getHearing().getFirst().getCaseList().getFirst();
        assertThat(caseObj.getReportingRestriction()).isFalse();
        assertThat(caseObj.getReportingRestrictionDetails()).isNull();

        // Then - offence level
        OffenceSchema offence = caseObj.getParty().getFirst().getOffence().getFirst();
        assertThat(offence.getReportingRestriction()).isNull();
        assertThat(offence.getReportingRestrictionDetails()).isNull();
    }

    @Test
    void transform_shouldSetNoReportingRestrictionWhenDefendantReportingRestrictionsIsNull() throws Exception {
        Hearing hearing = payload.getHearingDates().getFirst().getCourtRooms().getFirst()
                .getTimeslots().getFirst().getHearings().getFirst();
        hearing.getDefendants().getFirst().setReportingRestrictions(null);

        CourtListDocument document = transformationService.transform(payload);

        CaseSchema caseObj = document.getCourtLists().getFirst().getCourtHouse().getCourtRoom().getFirst()
                .getSession().getFirst().getSittings().getFirst().getHearing().getFirst().getCaseList().getFirst();
        assertThat(caseObj.getReportingRestriction()).isFalse();
        assertThat(caseObj.getReportingRestrictionDetails()).isNull();
    }

    @Test
    void transform_shouldIncludeApplicationsWhenHearingHasCourtApplication() throws Exception {
        // Given - hearing with courtApplicationId and courtApplication (applicant + respondents)
        Hearing hearing = payload.getHearingDates().getFirst().getCourtRooms().getFirst()
                .getTimeslots().getFirst().getHearings().getFirst();
        hearing.setCourtApplicationId("APP-REF-12345");
        hearing.setCourtApplication(CourtApplication.builder()
                .applicant(CourtApplicationParty.builder()
                        .name("Applicant Name")
                        .dateOfBirth("1 Jan 1990")
                        .build())
                .respondents(List.of(
                        CourtApplicationParty.builder()
                                .name("Respondent One")
                                .dateOfBirth("15 Sept 1985")
                                .build()
                ))
                .build());

        // When
        CourtListDocument document = transformationService.transform(payload);

        // Then - first hearing has one application
        List<Application> applications = document.getCourtLists().getFirst().getCourtHouse().getCourtRoom().getFirst()
                .getSession().getFirst().getSittings().getFirst().getHearing().getFirst().getApplication();
        assertThat(applications).hasSize(1);
        Application app = applications.getFirst();
        assertThat(app.getApplicationReference()).isEqualTo("APP-REF-12345");
        assertThat(app.getApplicationType()).isNull();
        assertThat(app.getApplicationParticulars()).isNull();
        assertThat(app.getReportingRestriction()).isFalse();
        assertThat(app.getParty()).hasSize(2); // applicant + one respondent
        assertThat(app.getParty().getFirst().getPartyRole()).isEqualTo("APPLICANT");
        assertThat(app.getParty().getFirst().getIndividualDetails().getIndividualSurname()).isEqualTo("Applicant Name");
        assertThat(app.getParty().getFirst().getIndividualDetails().getDateOfBirth()).isEqualTo("1990-01-01");
        assertThat(app.getParty().get(1).getPartyRole()).isEqualTo("RESPONDENT");
        assertThat(app.getParty().get(1).getIndividualDetails().getIndividualSurname()).isEqualTo("Respondent One");
        assertThat(app.getParty().get(1).getIndividualDetails().getDateOfBirth()).isEqualTo("1985-09-15");
    }

    /**
     * Loads CourtListPayload from a stub data JSON file
     */
    private CourtListPayload loadPayloadFromStubData(String resourcePath) throws Exception {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        String json = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        return objectMapper.readValue(json, CourtListPayload.class);
    }
}
