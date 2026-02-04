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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourtListQueryServiceTest {

    @Mock
    private ProgressionQueryService progressionQueryService;

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
    void buildCourtListDocumentFromPayload_shouldUseStandardTransformation_whenListIdIsStandard() {
        // Given
        when(transformationService.transform(payload)).thenReturn(standardDocument);
        doNothing().when(jsonSchemaValidatorService).validate(standardDocument, "schema/court-list-schema.json");

        // When
        CourtListDocument result = courtListQueryService.buildCourtListDocumentFromPayload(payload, CourtListType.STANDARD);

        // Then
        assertThat(result).isEqualTo(standardDocument);
        verify(transformationService).transform(payload);
        verify(publicCourtListTransformationService, never()).transform(any());
        verify(jsonSchemaValidatorService).validate(standardDocument, "schema/court-list-schema.json");
    }

    @Test
    void buildCourtListDocumentFromPayload_shouldUsePublicTransformation_whenListIdIsPublic() {
        // Given
        when(publicCourtListTransformationService.transform(payload)).thenReturn(publicDocument);
        doNothing().when(jsonSchemaValidatorService).validate(publicDocument, "schema/public-court-list-schema.json");

        // When
        CourtListDocument result = courtListQueryService.buildCourtListDocumentFromPayload(payload, CourtListType.PUBLIC);

        // Then
        assertThat(result).isEqualTo(publicDocument);
        verify(publicCourtListTransformationService).transform(payload);
        verify(transformationService, never()).transform(any());
        verify(jsonSchemaValidatorService).validate(publicDocument, "schema/public-court-list-schema.json");
    }

    @Test
    void getCourtListPayload_shouldEnrichPayloadAndReturn_whenReferenceDataPresent() {
        // Given
        UUID refId = UUID.fromString("f8254db1-1683-483e-afb3-b87fde5a0a26");
        when(progressionQueryService.getCourtListPayload(CourtListType.STANDARD, "courtId", "2026-01-05", "2026-01-12", "cjscppuid"))
                .thenReturn(payload);
        when(referenceDataService.getCourtCenterDataByCourtName("Test Court"))
                .thenReturn(Optional.of(CourtCentreData.builder()
                        .id(refId)
                        .ouCode("B01LY00")
                        .courtIdNumeric("325")
                        .build()));

        // When
        CourtListPayload result = courtListQueryService.getCourtListPayload(
                CourtListType.STANDARD, "courtId", "2026-01-05", "2026-01-12", "cjscppuid");

        // Then
        assertThat(result).isEqualTo(payload);
        assertThat(payload.getOuCode()).isEqualTo("B01LY00");
        assertThat(payload.getCourtId()).isEqualTo(refId.toString());
        assertThat(payload.getCourtIdNumeric()).isEqualTo("325");
        verify(progressionQueryService).getCourtListPayload(CourtListType.STANDARD, "courtId", "2026-01-05", "2026-01-12", "cjscppuid");
        verify(referenceDataService).getCourtCenterDataByCourtName("Test Court");
    }

    @Test
    void getCourtListPayload_shouldReturnPayloadWithoutEnrichment_whenReferenceDataEmpty() {
        // Given
        when(progressionQueryService.getCourtListPayload(CourtListType.STANDARD, "courtId", "2026-01-05", "2026-01-12", "cjscppuid"))
                .thenReturn(payload);
        when(referenceDataService.getCourtCenterDataByCourtName("Test Court")).thenReturn(Optional.empty());

        // When
        CourtListPayload result = courtListQueryService.getCourtListPayload(
                CourtListType.STANDARD, "courtId", "2026-01-05", "2026-01-12", "cjscppuid");

        // Then
        assertThat(result).isEqualTo(payload);
        assertThat(payload.getOuCode()).isNull();
        assertThat(payload.getCourtId()).isNull();
        verify(progressionQueryService).getCourtListPayload(CourtListType.STANDARD, "courtId", "2026-01-05", "2026-01-12", "cjscppuid");
    }

}
