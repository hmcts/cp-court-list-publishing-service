package uk.gov.hmcts.cp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import uk.gov.hmcts.cp.models.CourtListPayload;
import uk.gov.hmcts.cp.models.transformed.CourtListDocument;
import uk.gov.hmcts.cp.models.transformed.schema.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
class OnlinePublicCourtListTransformationServiceTest {

    private OnlinePublicCourtListTransformationService transformationService;
    private final ObjectMapper objectMapper = ObjectMapperConfig.getObjectMapper();
    private CourtListPayload payload;

    @BeforeEach
    void setUp() throws Exception {
        // Create transformation service
        transformationService = new OnlinePublicCourtListTransformationService();
        
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
        // Online public schema: no panel; channel and application are arrays (empty when no source data)
        assertThat(hearing.getPanel()).isNull();
        assertThat(hearing.getChannel()).isNotNull();
        assertThat(hearing.getChannel()).isEmpty();
        assertThat(hearing.getApplication()).isNotNull();
        assertThat(hearing.getApplication()).isEmpty();

        // For public lists, verify simplified case structure
        CaseSchema caseObj = hearing.getCaseList().get(0);
        assertThat(caseObj).isNotNull();
        assertThat(caseObj.getCaseUrn()).isNotNull();
        assertThat(caseObj.getParty()).isNotEmpty();
        // Online public schema has no caseSequenceIndicator
        assertThat(caseObj.getCaseSequenceIndicator()).isNull();
        
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

        // Stub payload has no reference data - document has nulls
        assertThat(document.getOuCode()).isNull();
        assertThat(document.getCourtId()).isNull();
        assertThat(document.getCourtIdNumeric()).isNull();
    }

    @Test
    void transform_shouldCopyReferenceDataFieldsFromPayloadToDocument() throws Exception {
        // Given - payload enriched with ouCode/courtId from getCourtCenterDataByCourtName
        payload.setOuCode("B01LY00");
        payload.setCourtId("f8254db1-1683-483e-afb3-b87fde5a0a26");
        payload.setCourtIdNumeric("325");

        // When
        CourtListDocument document = transformationService.transform(payload);

        // Then - reference data fields are present on document
        assertThat(document).isNotNull();
        assertThat(document.getOuCode()).isEqualTo("B01LY00");
        assertThat(document.getCourtId()).isEqualTo("f8254db1-1683-483e-afb3-b87fde5a0a26");
        assertThat(document.getCourtIdNumeric()).isEqualTo("325");
    }

    @Test
    void transform_shouldIncludeVenueAddress() throws Exception {
        // When
        CourtListDocument document = transformationService.transform(payload);

        // Then - schema requires venueAddress.line and venueAddress.postCode
        assertThat(document).isNotNull();
        Venue venue = document.getVenue();
        assertThat(venue).isNotNull();
        AddressSchema venueAddress = venue.getVenueAddress();
        assertThat(venueAddress).isNotNull();
        assertThat(venueAddress.getLine()).isNotNull();
        assertThat(venueAddress.getPostCode()).isNotNull(); // required by schema, never null
    }

    @Test
    void transform_shouldUseAddress1AndAddress2FromPayload() throws Exception {
        // Payload has address1, address2 (e.g. from enrichment or court centre fallback)
        payload.setAddress1("176A Lavender Hill London");
        payload.setAddress2("SW11 1JU");

        CourtListDocument document = transformationService.transform(payload);

        AddressSchema venueAddress = document.getVenue().getVenueAddress();
        assertThat(venueAddress.getLine()).hasSize(2);
        assertThat(venueAddress.getLine().get(0)).isEqualTo("176A Lavender Hill London");
        assertThat(venueAddress.getLine().get(1)).isEqualTo("SW11 1JU");
        assertThat(venueAddress.getPostCode()).isEmpty();
    }

    @Test
    void transform_shouldUseAddress1Address2AndPostcodeFromPayload() throws Exception {
        payload.setAddress1("176A Lavender Hill London");
        payload.setAddress2("SW11 1JU");
        payload.setPostcode("SW11 1JU");

        CourtListDocument document = transformationService.transform(payload);

        AddressSchema venueAddress = document.getVenue().getVenueAddress();
        assertThat(venueAddress.getLine()).hasSize(2);
        assertThat(venueAddress.getLine().get(0)).isEqualTo("176A Lavender Hill London");
        assertThat(venueAddress.getLine().get(1)).isEqualTo("SW11 1JU");
        assertThat(venueAddress.getPostCode()).isEqualTo("SW11 1JU");
    }

    @Test
    void transform_shouldUseVenueAddressFromReferenceDataWhenPresent() throws Exception {
        // Given – payload enriched with reference data (address1-5, postcode)
        payload.setAddress1("176A Lavender Hill");
        payload.setAddress2("London");
        payload.setPostcode("SW11 1JU");

        // When
        CourtListDocument document = transformationService.transform(payload);

        // Then – venue uses full address and postcode from reference data
        AddressSchema venueAddress = document.getVenue().getVenueAddress();
        assertThat(venueAddress.getLine()).hasSize(2);
        assertThat(venueAddress.getLine().get(0)).isEqualTo("176A Lavender Hill");
        assertThat(venueAddress.getLine().get(1)).isEqualTo("London");
        assertThat(venueAddress.getPostCode()).isEqualTo("SW11 1JU");
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
