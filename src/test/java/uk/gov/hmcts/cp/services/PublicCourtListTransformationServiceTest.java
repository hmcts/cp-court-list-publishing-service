package uk.gov.hmcts.cp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import uk.gov.hmcts.cp.models.CourtListPayload;
import uk.gov.hmcts.cp.models.transformed.CourtListDocument;
import uk.gov.hmcts.cp.models.transformed.schema.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
class PublicCourtListTransformationServiceTest {

    private PublicCourtListTransformationService transformationService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private CourtListPayload payload;

    @BeforeEach
    void setUp() throws Exception {
        // Create transformation service
        transformationService = new PublicCourtListTransformationService();
        
        payload = loadPayloadFromStubData("stubdata/court-list-payload-public.json");
    }

    @Test
    void transform_shouldTransformToSimplifiedFormat() throws Exception {
        // When
        CourtListDocument document = transformationService.transform(payload);

        // Then - Verify document structure matches new schema
        assertThat(document).isNotNull();
        assertThat(document.getDocument()).isNotNull();
        
        // Verify DocumentSchema with publicationDate
        DocumentSchema documentSchema = document.getDocument();
        assertThat(documentSchema.getPublicationDate()).isNotNull();
        assertThat(documentSchema.getPublicationDate()).matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}([.]\\d{1,3})?Z$");
        
        // Verify Venue
        Venue venue = document.getVenue();
        assertThat(venue).isNotNull();
        assertThat(venue.getVenueAddress()).isNotNull();
        
        // Verify CourtLists
        List<CourtList> courtLists = document.getCourtLists();
        assertThat(courtLists).isNotNull();
        assertThat(courtLists).isNotEmpty();
        
        // Verify structure
        CourtHouse courtHouse = courtLists.get(0).getCourtHouse();
        assertThat(courtHouse).isNotNull();
        assertThat(courtHouse.getCourtRoom()).isNotEmpty();
        
        CourtRoomSchema courtRoom = courtHouse.getCourtRoom().get(0);
        assertThat(courtRoom).isNotNull();
        assertThat(courtRoom.getSession()).isNotEmpty();
        
        SessionSchema session = courtRoom.getSession().get(0);
        assertThat(session).isNotNull();
        assertThat(session.getSittings()).isNotEmpty();
        
        Sitting sitting = session.getSittings().get(0);
        assertThat(sitting).isNotNull();
        assertThat(sitting.getHearing()).isNotEmpty();
        
        HearingSchema hearing = sitting.getHearing().get(0);
        assertThat(hearing).isNotNull();
        assertThat(hearing.getCaseList()).isNotEmpty();
        
        // For public lists, verify simplified case structure
        CaseSchema caseObj = hearing.getCaseList().get(0);
        assertThat(caseObj).isNotNull();
        assertThat(caseObj.getCaseUrn()).isNotNull();
        assertThat(caseObj.getParty()).isNotEmpty();
        
        // Verify party has minimal information (name only for public lists)
        Party party = caseObj.getParty().get(0);
        assertThat(party).isNotNull();
        assertThat(party.getPartyRole()).isEqualTo("DEFENDANT");
        assertThat(party.getIndividualDetails()).isNotNull();
        
        IndividualDetails individualDetails = party.getIndividualDetails();
        assertThat(individualDetails.getIndividualForenames()).isNotNull();
        assertThat(individualDetails.getIndividualSurname()).isNotNull();
        // Public lists should not include sensitive information like DOB, address, etc.
        assertThat(individualDetails.getDateOfBirth()).isNull();
        assertThat(individualDetails.getAddress()).isNull();
        assertThat(party.getOffence()).isNull();
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
    void transform_shouldHaveCorrectPublicationDate() throws Exception {
        // When
        CourtListDocument document = transformationService.transform(payload);

        // Then
        assertThat(document).isNotNull();
        DocumentSchema documentSchema = document.getDocument();
        assertThat(documentSchema.getPublicationDate()).isNotNull();
        // Verify it's a valid ISO 8601 date-time string
        assertThat(documentSchema.getPublicationDate()).matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}([.]\\d{1,3})?Z$");
    }

    private CourtListPayload loadPayloadFromStubData(String resourcePath) throws Exception {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        String json = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        return objectMapper.readValue(json, CourtListPayload.class);
    }

}
