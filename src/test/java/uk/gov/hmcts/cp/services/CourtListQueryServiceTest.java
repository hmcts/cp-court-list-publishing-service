package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.models.CourtListPayload;
import uk.gov.hmcts.cp.models.transformed.CourtListDocument;
import uk.gov.hmcts.cp.openapi.model.CourtListType;

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
        // Given
        when(listingQueryService.getCourtListPayload(CourtListType.STANDARD, "courtId", "2026-01-05", "2026-01-12", "cjscppuid"))
                .thenReturn(payload);
        when(transformationService.transform(payload))
                .thenReturn(standardDocument);
        doNothing().when(jsonSchemaValidatorService).validate(standardDocument, "schema/court-list-schema.json");

        // When
        CourtListDocument result = courtListQueryService.queryCourtList(CourtListType.STANDARD, "courtId", "2026-01-05", "2026-01-12", "cjscppuid");

        // Then
        assertThat(result).isEqualTo(standardDocument);
        verify(transformationService).transform(payload);
        verify(publicCourtListTransformationService, never()).transform(payload);
        verify(jsonSchemaValidatorService).validate(standardDocument, "schema/court-list-schema.json");
    }

    @Test
    void queryCourtList_shouldUsePublicTransformation_whenListIdIsPublic() {
        // Given
        when(listingQueryService.getCourtListPayload(CourtListType.PUBLIC, "courtId", "2026-01-05", "2026-01-12", "cjscppuid"))
                .thenReturn(payload);
        when(publicCourtListTransformationService.transform(payload))
                .thenReturn(publicDocument);
        doNothing().when(jsonSchemaValidatorService).validate(publicDocument, "schema/public-court-list-schema.json");

        // When
        CourtListDocument result = courtListQueryService.queryCourtList(CourtListType.PUBLIC, "courtId", "2026-01-05", "2026-01-12", "cjscppuid");

        // Then
        assertThat(result).isEqualTo(publicDocument);
        verify(publicCourtListTransformationService).transform(payload);
        verify(transformationService, never()).transform(payload);
        verify(jsonSchemaValidatorService).validate(publicDocument, "schema/public-court-list-schema.json");
    }
}
