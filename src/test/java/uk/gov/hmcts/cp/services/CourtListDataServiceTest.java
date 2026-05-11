package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.cp.models.CourtListPayload;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.services.courtlistdownload.CourtListDownloadException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourtListDataServiceTest {

    private static final String LISTING_BASE_URL = "https://internal.example.com";
    private static final String LISTING_PATH = "/listing-service/query/api/rest/listing/courtlistpayload";

    @Mock
    private RestTemplate publicCourtListRestTemplate;

    private CourtListDataService courtListDataService;

    @BeforeEach
    void setUp() {
        courtListDataService = new CourtListDataService(publicCourtListRestTemplate, LISTING_BASE_URL);
    }

    @Test
    void getCourtListDataReturnsListingPayloadAsIs() {
        String listingJson = "{\"listType\":\"standard\",\"courtCentreName\":\"Lavender Hill\",\"ouCode\":\"B01LY00\",\"courtId\":\"f8254db1-1683-483e-afb3-b87fde5a0a26\"}";
        when(publicCourtListRestTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(listingJson, HttpStatus.OK));

        String result = courtListDataService.getCourtListData(
                CourtListType.STANDARD,
                "f8254db1-1683-483e-afb3-b87fde5a0a26",
                null,
                "2024-01-15",
                "2024-01-15",
                false,
                "request-user-id",
                false);

        assertThat(result).isEqualTo(listingJson);
        verify(publicCourtListRestTemplate).exchange(
                argThat((String url) -> url.contains(LISTING_PATH)
                        && url.contains("listId=STANDARD")
                        && url.contains("courtCentreId=f8254db1-1683-483e-afb3-b87fde5a0a26")
                        && url.contains("startDate=2024-01-15")
                        && url.contains("endDate=2024-01-15")
                        && url.contains("restricted=false")
                        && url.contains("includeApplications=false")),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class));
    }

    @Test
    void getCourtListData_throwsWhenListingReturnsEmptyBody() {
        when(publicCourtListRestTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>((String) null, HttpStatus.OK));

        assertThatThrownBy(() -> courtListDataService.getCourtListData(
                CourtListType.PUBLIC,
                "f8254db1-1683-483e-afb3-b87fde5a0a26",
                null,
                "2024-01-15",
                "2024-01-15",
                false,
                "user",
                false))
                .isInstanceOf(CourtListDownloadException.class)
                .hasMessageContaining("empty response");
    }

    @Test
    void getCourtListPayloadAlwaysCallsListingWithRestrictedFalse() {
        String json = "{\"listType\":\"standard\",\"courtCentreName\":\"Test Court\",\"ouCode\":\"B01LY\",\"courtId\":\"f8254db1-1683-483e-afb3-b87fde5a0a26\"}";
        when(publicCourtListRestTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(json, HttpStatus.OK));

        CourtListPayload result = courtListDataService.getCourtListPayload(
                CourtListType.STANDARD, "courtCentre1", "2026-01-05", "2026-01-12", "user-id", false);

        assertThat(result).isNotNull();
        assertThat(result.getListType()).isEqualTo("standard");
        assertThat(result.getCourtCentreName()).isEqualTo("Test Court");
        assertThat(result.getOuCode()).isEqualTo("B01LY");
        assertThat(result.getCourtId()).isEqualTo("f8254db1-1683-483e-afb3-b87fde5a0a26");
        verify(publicCourtListRestTemplate).exchange(
                argThat((String url) -> url.contains("restricted=false")
                        && url.contains("includeApplications=false")),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class));
    }

    @Test
    void getCourtListPayload_throws_whenListingReturnsInvalidJson() {
        when(publicCourtListRestTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("not valid json {{{", HttpStatus.OK));

        assertThatThrownBy(() -> courtListDataService.getCourtListPayload(
                CourtListType.STANDARD, "courtCentre1", "2026-01-05", "2026-01-12", null, false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to parse court list payload");
    }

    @Test
    void getCourtListPayloadForDownloadCallsListingForSupportedType() {
        String json = "{\"listType\":\"standard\",\"templateName\":\"BenchAndStandardCourtList\"}";
        when(publicCourtListRestTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(json, HttpStatus.OK));

        String result = courtListDataService.getCourtListPayloadForDownload(
                CourtListType.STANDARD, "f8254db1-1683-483e-afb3-b87fde5a0a26", null,
                LocalDate.of(2026, 2, 27), LocalDate.of(2026, 2, 27), "user-id");

        assertThat(result).isEqualTo(json);
    }

    @Test
    void getCourtListPayloadForDownload_throwsWhenBaseUrlNotConfigured() {
        CourtListDataService serviceWithNoUrl = new CourtListDataService(publicCourtListRestTemplate, "");

        assertThatThrownBy(() -> serviceWithNoUrl.getCourtListPayloadForDownload(
                CourtListType.STANDARD, "f8254db1-1683-483e-afb3-b87fde5a0a26", null,
                LocalDate.of(2026, 2, 27), LocalDate.of(2026, 2, 27), "user-id"))
                .isInstanceOf(CourtListDownloadException.class)
                .hasMessageContaining("Court list data is not configured");
    }

    @Test
    void fetchCourtListPdfFromListingReturnsPdfBytesAndCallsCourtListEndpointWithRestrictedFalse() {
        byte[] pdfBytes = "%PDF-1.6 fake".getBytes();
        when(publicCourtListRestTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(pdfBytes, HttpStatus.OK));

        byte[] result = courtListDataService.fetchCourtListPdfFromListing(
                CourtListType.ALPHABETICAL, "f8254db1-1683-483e-afb3-b87fde5a0a26", null,
                LocalDate.of(2026, 2, 27), LocalDate.of(2026, 2, 27), "user-id");

        assertThat(result).isEqualTo(pdfBytes);
        verify(publicCourtListRestTemplate).exchange(
                argThat((String url) -> url.contains("/listing-service/query/api/rest/listing/courtlist")
                        && !url.contains("/courtlistpayload")
                        && url.contains("listId=ALPHABETICAL")
                        && url.contains("courtCentreId=f8254db1-1683-483e-afb3-b87fde5a0a26")
                        && url.contains("startDate=2026-02-27")
                        && url.contains("endDate=2026-02-27")
                        && url.contains("restricted=false")),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(byte[].class));
    }

    @Test
    void fetchCourtListPdfFromListing_throwsWhenListingReturnsEmptyBody() {
        when(publicCourtListRestTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(new byte[0], HttpStatus.OK));

        assertThatThrownBy(() -> courtListDataService.fetchCourtListPdfFromListing(
                CourtListType.JUDGE, "f8254db1-1683-483e-afb3-b87fde5a0a26", null,
                LocalDate.of(2026, 2, 27), LocalDate.of(2026, 2, 27), "user-id"))
                .isInstanceOf(CourtListDownloadException.class)
                .hasMessageContaining("empty response");
    }

    @Test
    void fetchCourtListPdfFromListing_throwsWhenBaseUrlNotConfigured() {
        CourtListDataService serviceWithNoUrl = new CourtListDataService(publicCourtListRestTemplate, "");

        assertThatThrownBy(() -> serviceWithNoUrl.fetchCourtListPdfFromListing(
                CourtListType.ALPHABETICAL, "f8254db1-1683-483e-afb3-b87fde5a0a26", null,
                LocalDate.of(2026, 2, 27), LocalDate.of(2026, 2, 27), "user-id"))
                .isInstanceOf(CourtListDownloadException.class)
                .hasMessageContaining("Court list data is not configured");
    }
}
