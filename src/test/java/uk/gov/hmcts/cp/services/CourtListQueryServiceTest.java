package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.models.CourtCentreData;
import uk.gov.hmcts.cp.models.CourtListPayload;
import uk.gov.hmcts.cp.models.transformed.CourtListDocument;
import uk.gov.hmcts.cp.openapi.model.CourtListType;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourtListQueryServiceTest {

    @Mock
    private ListingQueryService listingQueryService;

    @Mock
    private ReferenceDataService referenceDataService;

    @Mock
    private CourtListTransformationService transformationService;

    @Mock
    private PublicCourtListTransformationService publicCourtListTransformationService;

    @Mock
    private JsonSchemaValidatorService jsonSchemaValidatorService;

    @InjectMocks
    private CourtListQueryService courtListQueryService;

    private CourtListPayload payload;
    private CourtListDocument standardDocument;
    private CourtListDocument publicDocument;

    @BeforeEach
    void setUp() {
        payload = CourtListPayload.builder()
                .courtCentreName("Test Court")
                .build();

        standardDocument = CourtListDocument.builder().build();
        publicDocument = CourtListDocument.builder().build();
    }

    @Test
    void queryCourtList_shouldUseStandardTransformation_whenListIdIsNotPublic() {
        // Given - reference data returns court centre data (ouCode, courtId)
        UUID refId = UUID.fromString("f8254db1-1683-483e-afb3-b87fde5a0a26");
        when(referenceDataService.getCourtCenterDataByCourtName("Test Court"))
                .thenReturn(Optional.of(CourtCentreData.builder()
                        .id(refId)
                        .ouCode("B01LY00")
                        .courtIdNumeric("325")
                        .build()));
        when(listingQueryService.getCourtListPayload(CourtListType.STANDARD, "courtId", "2026-01-05", "2026-01-12", "cjscppuid"))
                .thenReturn(payload);
        when(transformationService.transform(payload))
                .thenReturn(standardDocument);
        doNothing().when(jsonSchemaValidatorService).validate(standardDocument, "schema/court-list-schema.json");

        // When
        CourtListDocument result = courtListQueryService.queryCourtList(CourtListType.STANDARD, "courtId", "2026-01-05", "2026-01-12", "cjscppuid");

        // Then
        assertThat(result).isEqualTo(standardDocument);
        assertThat(payload.getOuCode()).isEqualTo("B01LY00");
        assertThat(payload.getCourtId()).isEqualTo(refId.toString());
        assertThat(payload.getCourtIdNumeric()).isEqualTo("325");
        verify(referenceDataService).getCourtCenterDataByCourtName("Test Court");
        verify(transformationService).transform(payload);
        verify(publicCourtListTransformationService, never()).transform(payload);
        verify(jsonSchemaValidatorService).validate(standardDocument, "schema/court-list-schema.json");
    }

    @Test
    void queryCourtList_shouldUsePublicTransformation_whenListIdIsPublic() {
        // Given - reference data returns court centre data
        when(referenceDataService.getCourtCenterDataByCourtName("Test Court"))
                .thenReturn(Optional.of(CourtCentreData.builder()
                        .id(UUID.fromString("f8254db1-1683-483e-afb3-b87fde5a0a26"))
                        .ouCode("B01LY00")
                        .courtIdNumeric("325")
                        .build()));
        when(listingQueryService.getCourtListPayload(CourtListType.PUBLIC, "courtId", "2026-01-05", "2026-01-12", "cjscppuid"))
                .thenReturn(payload);
        when(publicCourtListTransformationService.transform(payload))
                .thenReturn(publicDocument);
        doNothing().when(jsonSchemaValidatorService).validate(publicDocument, "schema/public-court-list-schema.json");

        // When
        CourtListDocument result = courtListQueryService.queryCourtList(CourtListType.PUBLIC, "courtId", "2026-01-05", "2026-01-12", "cjscppuid");

        // Then
        assertThat(result).isEqualTo(publicDocument);
        assertThat(payload.getOuCode()).isEqualTo("B01LY00");
        verify(referenceDataService).getCourtCenterDataByCourtName("Test Court");
        verify(publicCourtListTransformationService).transform(payload);
        verify(transformationService, never()).transform(payload);
        verify(jsonSchemaValidatorService).validate(publicDocument, "schema/public-court-list-schema.json");
    }

    @Test
    void queryCourtList_shouldNotEnrichPayload_whenReferenceDataReturnsEmpty() {
        // Given - no reference data (e.g. API not configured or court not found)
        when(referenceDataService.getCourtCenterDataByCourtName("Test Court")).thenReturn(Optional.empty());
        when(listingQueryService.getCourtListPayload(CourtListType.STANDARD, "courtId", "2026-01-05", "2026-01-12", "cjscppuid"))
                .thenReturn(payload);
        when(transformationService.transform(payload)).thenReturn(standardDocument);
        doNothing().when(jsonSchemaValidatorService).validate(standardDocument, "schema/court-list-schema.json");

        // When
        courtListQueryService.queryCourtList(CourtListType.STANDARD, "courtId", "2026-01-05", "2026-01-12", "cjscppuid");

        // Then - payload ouCode/courtId remain null
        assertThat(payload.getOuCode()).isNull();
        assertThat(payload.getCourtId()).isNull();
        verify(referenceDataService).getCourtCenterDataByCourtName("Test Court");
    }
}
