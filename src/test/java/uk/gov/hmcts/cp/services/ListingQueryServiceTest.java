package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.cp.models.CourtListPayload;
import uk.gov.hmcts.cp.openapi.model.CourtListType;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingQueryServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private ListingQueryService listingQueryService;

    private CourtListType courtListType;
    private String courtCentreId;
    private String startDate;
    private String endDate;
    private String cjscppuid;

    @BeforeEach
    void setUp() {
        // Use reflection to set the base URL for testing
        String baseUrl = "https://test.example.com";
        try {
            java.lang.reflect.Field field = ListingQueryService.class.getDeclaredField("commonPlatformQueryApiBaseUrl");
            field.setAccessible(true);
            field.set(listingQueryService, baseUrl);
        } catch (Exception e) {
            // Ignore if reflection fails
        }

        courtListType = CourtListType.STANDARD;
        courtCentreId = "f8254db1-1683-483e-afb3-b87fde5a0a26";
        startDate = "2026-01-05";
        endDate = "2026-01-12";
        cjscppuid = "ad0920bd-521a-4f40-b942-f82d258ea3cc";
    }

    @Test
    void getCourtListPayload_shouldCallApiWithCorrectParametersAndHeaders() {
        // Given
        CourtListPayload expectedPayload = CourtListPayload.builder()
                .listType("standard")
                .courtCentreName("Test Court Centre")
                .courtCentreDefaultStartTime("10:00:00")
                .courtCentreAddress1("123 Test Street")
                .courtCentreAddress2("Test City")
                .welshCourtCentreName("Llys Profi")
                .templateName("BenchAndStandardCourtList")
                .build();

        ResponseEntity<CourtListPayload> responseEntity = new ResponseEntity<>(expectedPayload, HttpStatus.OK);

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        ArgumentCaptor<HttpEntity<?>> httpEntityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        when(restTemplate.exchange(
                any(URI.class),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(CourtListPayload.class)
        )).thenReturn(responseEntity);

        // When
        CourtListPayload result = listingQueryService.getCourtListPayload(courtListType, courtCentreId, startDate, endDate, cjscppuid);

        // Then - Verify the result
        assertThat(result).isNotNull();
        assertThat(result.getListType()).isEqualTo("standard");
        assertThat(result.getCourtCentreName()).isEqualTo("Test Court Centre");
        assertThat(result.getCourtCentreDefaultStartTime()).isEqualTo("10:00:00");
        assertThat(result.getCourtCentreAddress1()).isEqualTo("123 Test Street");
        assertThat(result.getCourtCentreAddress2()).isEqualTo("Test City");
        assertThat(result.getWelshCourtCentreName()).isEqualTo("Llys Profi");
        assertThat(result.getTemplateName()).isEqualTo("BenchAndStandardCourtList");

        // Verify RestTemplate was called
        verify(restTemplate).exchange(
                uriCaptor.capture(),
                eq(HttpMethod.GET),
                httpEntityCaptor.capture(),
                eq(CourtListPayload.class)
        );

        // Verify URI contains correct path and query parameters
        URI capturedUri = uriCaptor.getValue();
        assertThat(capturedUri.toString()).contains("/listing-query-api/query/api/rest/listing/courtlistpayload");
        assertThat(capturedUri.toString()).contains("listId=STANDARD");
        assertThat(capturedUri.toString()).contains("courtCentreId=f8254db1-1683-483e-afb3-b87fde5a0a26");
        assertThat(capturedUri.toString()).contains("startDate=2026-01-05");
        assertThat(capturedUri.toString()).contains("endDate=2026-01-12");

        // Verify headers are set correctly
        HttpEntity<?> capturedEntity = httpEntityCaptor.getValue();
        assertThat(capturedEntity).isNotNull();
        HttpHeaders headers = capturedEntity.getHeaders();
        assertThat(headers).isNotNull();
        assertThat(headers.getFirst("Accept")).isEqualTo("application/vnd.listing.search.court.list.payload+json");
        assertThat(headers.getFirst("CJSCPPUID")).isEqualTo(cjscppuid);
    }

    @Test
    void getCourtListPayload_shouldThrowException_whenRestClientExceptionOccurs() {
        // Given
        when(restTemplate.exchange(
                any(URI.class),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(CourtListPayload.class)
        )).thenThrow(new RestClientException("Connection failed"));

        // When & Then
        assertThatThrownBy(() -> listingQueryService.getCourtListPayload(courtListType, courtCentreId, startDate, endDate, cjscppuid))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to fetch court list payload from common-platform-query-api");
    }

}
