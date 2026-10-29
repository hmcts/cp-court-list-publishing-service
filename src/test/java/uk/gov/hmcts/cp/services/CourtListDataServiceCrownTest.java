package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.services.courtlistdownload.CourtListDownloadException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourtListDataServiceCrownTest {

    private static final String BASE_URL = "http://listing.test";
    private static final String CROWN_MEDIA_TYPE = "application/vnd.listing.search.crown.list.payload+json";
    private static final String COURT_CENTRE_ID = "b2c3d4e5-f6a7-8901-bcde-f12345678901";
    private static final LocalDate START = LocalDate.parse("2026-11-02");
    private static final LocalDate END = LocalDate.parse("2026-11-08");

    @Mock
    private ProgressionQueryService progressionQueryService;
    @Mock
    private RestTemplate restTemplate;

    private CourtListDataService service;

    @BeforeEach
    void setUp() {
        service = new CourtListDataService(progressionQueryService, restTemplate, BASE_URL);
    }

    @Test
    void asksTheDailyListResourceForTheCrownMediaTypeWithDatesForFinal() {
        stubResponse("{}");

        service.getCrownListPayload(CourtListType.FINAL, COURT_CENTRE_ID, START, START, "user-1");

        final ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<HttpEntity> request = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(url.capture(), eq(HttpMethod.GET), request.capture(), eq(String.class));
        assertThat(url.getValue()).startsWith(BASE_URL + "/listing-service/query/api/rest/listing/dailylistpayload?")
                .contains("publishCourtListType=FINAL")
                .contains("courtCentreId=" + COURT_CENTRE_ID)
                .contains("startDate=2026-11-02")
                .contains("endDate=2026-11-02")
                .doesNotContain("weekCommencing");
        assertThat(request.getValue().getHeaders().getFirst("Accept")).isEqualTo(CROWN_MEDIA_TYPE);
        assertThat(request.getValue().getHeaders().getFirst("CJSCPPUID")).isEqualTo("user-1");
    }

    @Test
    void usesWeekCommencingDatesForTheAdvanceAndFirmLists() {
        stubResponse("{}");

        service.getCrownListPayload(CourtListType.WARN, COURT_CENTRE_ID, START, END, "user-1");
        service.getCrownListPayload(CourtListType.FIRM, COURT_CENTRE_ID, START, END, "user-1");

        final ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(restTemplate, org.mockito.Mockito.times(2)).exchange(url.capture(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        assertThat(url.getAllValues().get(0)).contains("publishCourtListType=WARN")
                .contains("weekCommencingStartDate=2026-11-02").contains("weekCommencingEndDate=2026-11-08")
                .doesNotContain("startDate=2026-11-02&").doesNotContain("&endDate=");
        assertThat(url.getAllValues().get(1)).contains("publishCourtListType=FIRM")
                .contains("weekCommencingStartDate=2026-11-02").contains("weekCommencingEndDate=2026-11-08");
    }

    @Test
    void theExistingDailyListQueryStillUsesTheDailyListMediaType() {
        stubResponse("{}");

        service.getCrownCourtDailyListPayload(CourtListType.FINAL, COURT_CENTRE_ID, null, START, START, "user-1", false);

        final ArgumentCaptor<HttpEntity> request = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.GET), request.capture(), eq(String.class));
        assertThat(request.getValue().getHeaders().getFirst("Accept")).isEqualTo("application/vnd.listing.search.daily.list.payload+json");
    }

    @Test
    void failsWhenTheListingContextReturnsNothing() {
        stubResponse("");

        assertThatThrownBy(() -> service.getCrownListPayload(CourtListType.FINAL, COURT_CENTRE_ID, START, START, "user-1"))
                .isInstanceOf(CourtListDownloadException.class)
                .hasMessageContaining("empty response");
    }

    @Test
    void failsWhenTheListingContextCannotBeReached() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("connection refused"));

        assertThatThrownBy(() -> service.getCrownListPayload(CourtListType.FINAL, COURT_CENTRE_ID, START, START, "user-1"))
                .isInstanceOf(CourtListDownloadException.class)
                .hasMessageContaining("Failed to fetch court list payload");
    }

    @Test
    void failsWhenTheListingBaseUrlIsNotConfigured() {
        final CourtListDataService unconfigured = new CourtListDataService(progressionQueryService, restTemplate, "");

        assertThatThrownBy(() -> unconfigured.getCrownListPayload(CourtListType.FINAL, COURT_CENTRE_ID, START, START, "user-1"))
                .isInstanceOf(CourtListDownloadException.class)
                .hasMessageContaining("not configured");
    }

    private void stubResponse(final String body) {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(body));
    }
}
