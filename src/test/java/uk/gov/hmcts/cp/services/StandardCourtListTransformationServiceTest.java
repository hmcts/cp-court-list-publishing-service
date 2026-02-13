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
        CourtList courtList = courtLists.get(0);
        assertThat(courtList).isNotNull();
        assertThat(courtList.getCourtHouse()).isNotNull();
        
        // Verify CourtHouse
        CourtHouse courtHouse = courtList.getCourtHouse();
        assertThat(courtHouse.getCourtHouseName()).isEqualTo("Lavender Hill Magistrates' Court");
        assertThat(courtHouse.getLja()).isEqualTo("Lavender Hill Magistrates' Court");
        assertThat(courtHouse.getCourtRoom()).isNotEmpty();
        
        // Verify first CourtRoom
        CourtRoomSchema courtRoom = courtHouse.getCourtRoom().get(0);
        assertThat(courtRoom).isNotNull();
        assertThat(courtRoom.getCourtRoomName()).isEqualTo("Courtroom 01");
        assertThat(courtRoom.getSession()).isNotEmpty();
        
        // Verify first Session
        SessionSchema session = courtRoom.getSession().get(0);
        assertThat(session).isNotNull();
        assertThat(session.getSittings()).isNotEmpty();
        
        // Verify first Sitting
        Sitting sitting = session.getSittings().get(0);
        assertThat(sitting).isNotNull();
        assertThat(sitting.getSittingStart()).isNotNull();
        // Verify sittingStart is in ISO 8601 format
        assertThat(sitting.getSittingStart()).matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}([.]\\d{1,3})?Z$");
        assertThat(sitting.getHearing()).isNotEmpty();
        
        // Verify first Hearing
        HearingSchema hearing = sitting.getHearing().get(0);
        assertThat(hearing).isNotNull();
        assertThat(hearing.getHearingType()).isEqualTo("First hearing");
        assertThat(hearing.getCaseList()).isNotEmpty();
        // Schema: channel and application are arrays (empty when no source data)
        assertThat(hearing.getChannel()).isNotNull();
        assertThat(hearing.getChannel()).isEmpty();
        assertThat(hearing.getApplication()).isNotNull();
        assertThat(hearing.getApplication()).isEmpty();
        
        // Verify first Case
        CaseSchema caseObj = hearing.getCaseList().get(0);
        assertThat(caseObj).isNotNull();
        assertThat(caseObj.getCaseUrn()).isEqualTo("29GD1486826"); // From payload
        assertThat(caseObj.getParty()).isNotEmpty();
        
        // Verify first Party (DEFENDANT)
        Party party = caseObj.getParty().get(0);
        assertThat(party).isNotNull();
        assertThat(party.getPartyRole()).isEqualTo("DEFENDANT");
        assertThat(party.getIndividualDetails()).isNotNull();
        
        // Verify IndividualDetails
        IndividualDetails individualDetails = party.getIndividualDetails();
        assertThat(individualDetails.getIndividualForenames()).isEqualTo("Robert"); // From payload
        assertThat(individualDetails.getIndividualSurname()).isEqualTo("Ormsby"); // From payload
        assertThat(individualDetails.getDateOfBirth()).isEqualTo("1964-01-13"); // Converted from "13 Jan 1964" to ISO date (yyyy-MM-dd) per schema
        assertThat(individualDetails.getAge()).isEqualTo(62); // Converted from "62"
        
        // Verify Address transformation
        AddressSchema address = individualDetails.getAddress();
        assertThat(address).isNotNull();
        assertThat(address.getLine()).isNotEmpty();
        assertThat(address.getLine().get(0)).isEqualTo("True Close"); // From payload address1
        assertThat(address.getLine().get(1)).isEqualTo("StreetDescription"); // From payload address2
        assertThat(address.getLine().get(2)).isEqualTo("Locality2O"); // From payload address3
        
        // Verify Offences transformation
        List<OffenceSchema> offences = party.getOffence();
        assertThat(offences).isNotNull();
        assertThat(offences).isNotEmpty();

        // Verify first Offence
        OffenceSchema offence = offences.get(0);
        assertThat(offence).isNotNull();
        assertThat(offence.getOffenceCode()).isEqualTo("72357c7f-c4d1-4027-bed3-d42fb52a164e"); // From offence id
        assertThat(offence.getOffenceTitle()).isEqualTo("Occupy reserved seat / berth without a valid ticket on the Tyne and Wear Metro"); // From payload
        assertThat(offence.getOffenceWording()).contains("Has a violent past"); // From payload

        // Stub payload has no ouCode/courtId/courtIdNumeric (reference data not in stub) - document has nulls
        assertThat(document.getOuCode()).isNull();
        assertThat(document.getCourtId()).isNull();
        assertThat(document.getCourtIdNumeric()).isNull();

        // Verify second Party (PROSECUTING_AUTHORITY) if exists
        if (caseObj.getParty().size() > 1) {
            Party prosecutorParty = caseObj.getParty().get(1);
            assertThat(prosecutorParty).isNotNull();
            assertThat(prosecutorParty.getPartyRole()).isEqualTo("PROSECUTING_AUTHORITY");
            assertThat(prosecutorParty.getOrganisationDetails()).isNotNull();
            assertThat(prosecutorParty.getOrganisationDetails().getOrganisationName()).isEqualTo("DERPF"); // From prosecutorType
        }
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
        
        CourtHouse courtHouse = courtLists.get(0).getCourtHouse();
        List<CourtRoomSchema> courtRooms = courtHouse.getCourtRoom();
        assertThat(courtRooms.size()).isGreaterThanOrEqualTo(2); // Multiple court rooms
        
        // Verify first court room
        CourtRoomSchema courtRoom1 = courtRooms.get(0);
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
            CourtHouse courtHouse = courtLists.get(0).getCourtHouse();
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
        
        CourtHouse courtHouse = courtLists.get(0).getCourtHouse();
        if (!courtHouse.getCourtRoom().isEmpty()) {
            CourtRoomSchema courtRoom = courtHouse.getCourtRoom().get(0);
            if (!courtRoom.getSession().isEmpty()) {
                SessionSchema session = courtRoom.getSession().get(0);
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
    void transform_shouldCopyReferenceDataFieldsFromPayloadToDocument() throws Exception {
        // Given - payload enriched with ouCode/courtId from getCourtCenterDataByCourtName
        payload.setOuCode("B01LY00");
        payload.setCourtId("f8254db1-1683-483e-afb3-b87fde5a0a26");
        payload.setCourtIdNumeric("325");

        // When
        CourtListDocument document = transformationService.transform(payload);

        // Then - reference data fields are present on document (for CaTH and list publishing)
        assertThat(document).isNotNull();
        assertThat(document.getOuCode()).isEqualTo("B01LY00");
        assertThat(document.getCourtId()).isEqualTo("f8254db1-1683-483e-afb3-b87fde5a0a26");
        assertThat(document.getCourtIdNumeric()).isEqualTo("325");
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
