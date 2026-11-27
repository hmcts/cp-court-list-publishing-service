package uk.gov.hmcts.cp.services.crown;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrownListDataClientTest {

    private static final UUID COURT_CENTRE_ID = UUID.fromString("f8254db1-1683-483e-afb3-b87fde5a0a26");

    @Mock
    private RestTemplate restTemplate;

    @Test
    void fetchesADailyListWithStartAndEndDates() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"courtLists\":[]}"));

        String body = client().fetch(request(CrownListType.DAILY, LocalDate.of(2026, 6, 9), LocalDate.of(2026, 6, 9)));

        assertThat(body).isEqualTo("{\"courtLists\":[]}");
        verify(restTemplate).exchange(
                argThat((String url) -> url.startsWith("http://listing" + CrownListDataClient.PATH)
                        && url.contains("publishCourtListType=FINAL") && url.contains("courtCentreId=" + COURT_CENTRE_ID)
                        && url.contains("startDate=2026-06-09") && url.contains("endDate=2026-06-09")
                        && !url.contains("weekCommencing")),
                eq(HttpMethod.GET),
                argThat((HttpEntity<?> entity) -> entity.getHeaders().getAccept().contains(CrownListDataClient.CROWN_LIST_PAYLOAD)
                        && "user-id".equals(entity.getHeaders().getFirst("CJSCPPUID"))),
                eq(String.class));
    }

    @Test
    void fetchesAWeekListWithWeekCommencingDates() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"));

        client().fetch(request(CrownListType.WARNED, LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 14)));

        verify(restTemplate).exchange(
                argThat((String url) -> url.contains("publishCourtListType=WARN")
                        && url.contains("weekCommencingStartDate=2026-06-08") && url.contains("weekCommencingEndDate=2026-06-14")
                        && !url.contains("&startDate=")),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void failsOnAnEmptyResponse() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(" "));
        CrownListDataClient client = client();
        CrownPublishRequest request = request(CrownListType.FIRM, LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 14));

        assertThatThrownBy(() -> client.fetch(request)).isInstanceOf(IllegalStateException.class).hasMessageContaining("empty");
    }

    @Test
    void failsWhenListingCannotBeReached() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("connection refused"));
        CrownListDataClient client = client();
        CrownPublishRequest request = request(CrownListType.DAILY, LocalDate.of(2026, 6, 9), LocalDate.of(2026, 6, 9));

        assertThatThrownBy(() -> client.fetch(request)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to fetch crown list payload");
    }

    @Test
    void failsWhenTheBaseUrlIsNotConfigured() {
        CrownListDataClient client = new CrownListDataClient(restTemplate, "");
        CrownPublishRequest request = request(CrownListType.DAILY, LocalDate.of(2026, 6, 9), LocalDate.of(2026, 6, 9));

        assertThatThrownBy(() -> client.fetch(request)).isInstanceOf(IllegalStateException.class).hasMessageContaining("base-url");
    }

    private CrownListDataClient client() {
        return new CrownListDataClient(restTemplate, "http://listing");
    }

    private static CrownPublishRequest request(CrownListType type, LocalDate startDate, LocalDate endDate) {
        return new CrownPublishRequest(UUID.randomUUID(), COURT_CENTRE_ID, type, startDate, endDate, "user-id", "325", null);
    }
}
