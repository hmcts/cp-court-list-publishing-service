package uk.gov.hmcts.cp.http;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicCourtListControllerIntegrationTest {

    private static final String BASE_URL = System.getProperty("app.baseUrl", "http://localhost:8082/courtlistpublishing-service");
    private static final String COURT_CENTRE_ID = "f8254db1-1683-483e-afb3-b87fde5a0a26";
    private static final String START_DATE = "2026-02-27";
    private static final String END_DATE = "2026-02-27";

    private final RestTemplate restTemplate = new RestTemplate();

    @Test
    void getPublicCourtListPdfReturns400WhenCourtCentreIdMissing() {
        String url = BASE_URL + "/api/public-court-list?startDate=" + START_DATE + "&endDate=" + END_DATE;
        assertThatThrownBy(() -> restTemplate.getForEntity(url, String.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> assertThat(((HttpClientErrorException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void getPublicCourtListPdfReturns400WhenEndDateBeforeStartDate() {
        String url = BASE_URL + "/api/public-court-list?courtCentreId=" + COURT_CENTRE_ID + "&startDate=2026-02-28&endDate=2026-02-27";
        assertThatThrownBy(() -> restTemplate.getForEntity(url, String.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> assertThat(((HttpClientErrorException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void getPublicCourtListPdfReturns400WhenStartDateMissing() {
        String url = BASE_URL + "/api/public-court-list?courtCentreId=" + COURT_CENTRE_ID + "&endDate=" + END_DATE;
        assertThatThrownBy(() -> restTemplate.getForEntity(url, String.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> assertThat(((HttpClientErrorException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
