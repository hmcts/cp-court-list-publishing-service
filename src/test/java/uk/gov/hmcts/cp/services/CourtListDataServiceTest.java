package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.config.CourtListPublishingSystemUserConfig;
import uk.gov.hmcts.cp.models.CourtCentreData;
import uk.gov.hmcts.cp.models.CourtListPayload;
import uk.gov.hmcts.cp.models.LjaDetails;
import uk.gov.hmcts.cp.openapi.model.CourtListType;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourtListDataServiceTest {

    @Mock
    private ListingQueryService listingQueryService;

    @Mock
    private ReferenceDataService referenceDataService;

    @Mock
    private CourtListPublishingSystemUserConfig systemUserConfig;

    @InjectMocks
    private CourtListDataService courtListDataService;

    @BeforeEach
    void setUp() {
        lenient().when(systemUserConfig.getSystemUserId()).thenReturn("ba4e97ab-2174-4fa2-abfe-3ac2bb04bc75");
    }

    @Test
    void getCourtListData_returnsListingPayloadEnrichedWithOuCodeAndCourtId() {
        String listingJson = "{\"listType\":\"standard\",\"courtCentreName\":\"Lavender Hill Magistrates' Court\",\"templateName\":\"PublicCourtList\"}";
        UUID courtId = UUID.randomUUID();
        CourtCentreData refData = CourtCentreData.builder()
                .id(courtId)
                .ouCode("123")
                .courtIdNumeric("325")
                .build();

        when(listingQueryService.getCourtListPayload(
                eq(CourtListType.STANDARD),
                eq("f8254db1-1683-483e-afb3-b87fde5a0a26"),
                isNull(),
                eq("2024-01-15"),
                eq("2024-01-15"),
                eq(false),
                eq("request-user-id")))
                .thenReturn(listingJson);
        when(referenceDataService.getCourtCenterDataByCourtCentreId(eq("f8254db1-1683-483e-afb3-b87fde5a0a26"), eq("system-user-id")))
                .thenReturn(Optional.of(refData));

        String result = courtListDataService.getCourtListData(
                CourtListType.STANDARD,
                "f8254db1-1683-483e-afb3-b87fde5a0a26",
                null,
                "2024-01-15",
                "2024-01-15",
                false,
                "request-user-id",
                "system-user-id");

        assertThat(result).contains("\"ouCode\":\"123\"");
        assertThat(result).contains("\"courtId\":\"" + courtId + "\"");
        assertThat(result).contains("\"courtIdNumeric\":\"325\"");
        assertThat(result).contains("Lavender Hill Magistrates' Court");
        verify(listingQueryService).getCourtListPayload(
                eq(CourtListType.STANDARD),
                eq("f8254db1-1683-483e-afb3-b87fde5a0a26"),
                isNull(),
                eq("2024-01-15"),
                eq("2024-01-15"),
                eq(false),
                eq("request-user-id"));
        verify(referenceDataService).getCourtCenterDataByCourtCentreId(eq("f8254db1-1683-483e-afb3-b87fde5a0a26"), eq("system-user-id"));
    }

    @Test
    void getCourtListData_enrichesWithLjaDetailsWhenCourtCentreHasLja() {
        String listingJson = "{\"listType\":\"standard\",\"courtCentreName\":\"Lavender Hill\"}";
        CourtCentreData refData = CourtCentreData.builder()
                .id(UUID.randomUUID())
                .ouCode("B01LY00")
                .courtIdNumeric("325")
                .lja("2577")
                .oucodeL3Name("South Western (Lavender Hill)")
                .build();
        LjaDetails ljaDetails = LjaDetails.builder()
                .ljaCode("2577")
                .ljaName("Lavender Hill Magistrates' Court")
                .welshLjaName("Llys Y Goron Lavender Hill")
                .build();

        when(listingQueryService.getCourtListPayload(any(), any(), any(), any(), any(), anyBoolean(), any())).thenReturn(listingJson);
        when(referenceDataService.getCourtCenterDataByCourtCentreId(eq("f8254db1-1683-483e-afb3-b87fde5a0a26"), eq("system-user-id")))
                .thenReturn(Optional.of(refData));
        when(referenceDataService.getLjaDetails(eq("2577"), eq("system-user-id"))).thenReturn(Optional.of(ljaDetails));

        String result = courtListDataService.getCourtListData(
                CourtListType.STANDARD,
                "f8254db1-1683-483e-afb3-b87fde5a0a26",
                null,
                "2024-01-15",
                "2024-01-15",
                false,
                "request-user-id",
                "system-user-id");

        assertThat(result).contains("\"ljaCode\":\"2577\"");
        assertThat(result).contains("\"ljaName\":\"Lavender Hill Magistrates' Court\"");
        assertThat(result).contains("\"welshLjaName\":\"Llys Y Goron Lavender Hill\"");
        verify(referenceDataService).getLjaDetails(eq("2577"), eq("system-user-id"));
    }

    @Test
    void getCourtListData_returnsListingPayloadAsIsWhenReferenceDataEmpty() {
        String listingJson = "{\"listType\":\"public\",\"courtCentreName\":\"Unknown Court\"}";
        when(listingQueryService.getCourtListPayload(any(), any(), any(), any(), any(), anyBoolean(), any())).thenReturn(listingJson);
        when(referenceDataService.getCourtCenterDataByCourtCentreId(eq("f8254db1-1683-483e-afb3-b87fde5a0a26"), any())).thenReturn(Optional.empty());

        String result = courtListDataService.getCourtListData(
                CourtListType.PUBLIC,
                "f8254db1-1683-483e-afb3-b87fde5a0a26",
                null,
                "2024-01-15",
                "2024-01-15",
                false,
                "listing-user",
                "ref-data-user");

        assertThat(result).isEqualTo(listingJson);
    }

    @Test
    void getCourtListPayload_returnsDeserializedPayload_whenGetCourtListDataReturnsValidJson() {
        when(listingQueryService.getCourtListPayload(
                eq(CourtListType.STANDARD),
                eq("courtCentre1"),
                isNull(),
                eq("2026-01-05"),
                eq("2026-01-12"),
                eq(true),
                eq("user-id")))
                .thenReturn("{\"listType\":\"standard\",\"courtCentreName\":\"Test Court\"}");
        when(referenceDataService.getCourtCenterDataByCourtCentreId(eq("courtCentre1"), any()))
                .thenReturn(Optional.of(CourtCentreData.builder()
                        .id(UUID.fromString("f8254db1-1683-483e-afb3-b87fde5a0a26"))
                        .ouCode("B01LY")
                        .courtIdNumeric("325")
                        .build()));

        CourtListPayload result = courtListDataService.getCourtListPayload(
                CourtListType.STANDARD, "courtCentre1", "2026-01-05", "2026-01-12", "user-id");

        assertThat(result).isNotNull();
        assertThat(result.getListType()).isEqualTo("standard");
        assertThat(result.getCourtCentreName()).isEqualTo("Test Court");
        assertThat(result.getOuCode()).isEqualTo("B01LY");
        assertThat(result.getCourtId()).isEqualTo("f8254db1-1683-483e-afb3-b87fde5a0a26");
        assertThat(result.getCourtIdNumeric()).isEqualTo("325");
    }

    @Test
    void getCourtListPayload_usesRestrictedFalse_whenCjscppuidIsNull() {
        when(listingQueryService.getCourtListPayload(
                eq(CourtListType.PUBLIC),
                eq("courtCentre1"),
                isNull(),
                eq("2026-01-05"),
                eq("2026-01-12"),
                eq(false),
                isNull()))
                .thenReturn("{\"listType\":\"public\",\"courtCentreName\":\"A Court\"}");
        when(referenceDataService.getCourtCenterDataByCourtCentreId(eq("courtCentre1"), any())).thenReturn(Optional.empty());

        CourtListPayload result = courtListDataService.getCourtListPayload(
                CourtListType.PUBLIC, "courtCentre1", "2026-01-05", "2026-01-12", null);

        assertThat(result).isNotNull();
        assertThat(result.getCourtCentreName()).isEqualTo("A Court");
    }

    @Test
    void getCourtListPayload_throws_whenGetCourtListDataReturnsInvalidJson() {
        when(listingQueryService.getCourtListPayload(any(), any(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn("not valid json {{{");

        assertThatThrownBy(() -> courtListDataService.getCourtListPayload(
                CourtListType.STANDARD, "courtCentre1", "2026-01-05", "2026-01-12", null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to parse court list payload");
    }
}
