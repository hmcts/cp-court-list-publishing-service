package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.models.CourtCentreData;
import uk.gov.hmcts.cp.openapi.model.CourtListType;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourtListDataServiceTest {

    @Mock
    private ListingQueryService listingQueryService;

    @Mock
    private ReferenceDataService referenceDataService;

    @InjectMocks
    private CourtListDataService courtListDataService;

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
        when(referenceDataService.getCourtCenterDataByCourtName(eq("Lavender Hill Magistrates' Court"), eq("genesis-user-id")))
                .thenReturn(Optional.of(refData));

        String result = courtListDataService.getCourtListData(
                CourtListType.STANDARD,
                "f8254db1-1683-483e-afb3-b87fde5a0a26",
                null,
                "2024-01-15",
                "2024-01-15",
                false,
                "request-user-id",
                "genesis-user-id");

        assertThat(result).contains("\"ouCode\":\"123\"");
        assertThat(result).contains("\"courtId\":\"" + courtId + "\"");
        assertThat(result).contains("Lavender Hill Magistrates' Court");
        verify(listingQueryService).getCourtListPayload(
                eq(CourtListType.STANDARD),
                eq("f8254db1-1683-483e-afb3-b87fde5a0a26"),
                isNull(),
                eq("2024-01-15"),
                eq("2024-01-15"),
                eq(false),
                eq("request-user-id"));
        verify(referenceDataService).getCourtCenterDataByCourtName(eq("Lavender Hill Magistrates' Court"), eq("genesis-user-id"));
    }

    @Test
    void getCourtListData_returnsListingPayloadAsIsWhenReferenceDataEmpty() {
        String listingJson = "{\"listType\":\"public\",\"courtCentreName\":\"Unknown Court\"}";
        when(listingQueryService.getCourtListPayload(any(), any(), any(), any(), any(), anyBoolean(), any())).thenReturn(listingJson);
        when(referenceDataService.getCourtCenterDataByCourtName(eq("Unknown Court"), any())).thenReturn(Optional.empty());

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
}
