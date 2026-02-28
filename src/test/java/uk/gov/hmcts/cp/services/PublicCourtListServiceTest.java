package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.cp.services.publiccourtlist.PublicCourtListException;
import uk.gov.hmcts.cp.services.publiccourtlist.PublicCourtListService;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PublicCourtListServiceTest {

    private static final String COURT_CENTRE_ID = "f8254db1-1683-483e-afb3-b87fde5a0a26";
    private static final LocalDate START_DATE = LocalDate.of(2026, 2, 27);
    private static final LocalDate END_DATE = LocalDate.of(2026, 2, 27);
    private static final byte[] PDF_BYTES = new byte[]{1, 2, 3};
    private static final String PROGRESSION_URL = "https://progression.example/";
    private static final String DOC_GEN_URL = "https://docgen.example/";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private PublicCourtListService service;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new PublicCourtListService(restTemplate, PROGRESSION_URL, DOC_GEN_URL);
    }

    @Test
    void generatePublicCourtListPdf_returnsPdf_whenBothCallsSucceed() {
        String courtListJson = "{\"templateName\":\"PublicCourtList\",\"listType\":\"public\",\"courtCentreName\":\"Test Court\"}";
        server.expect(requestTo(org.hamcrest.Matchers.containsString("courtlist")))
                .andRespond(withSuccess(courtListJson, MediaType.parseMediaType("application/vnd.progression.search.court.list+json")));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("generate-pdf")))
                .andRespond(withSuccess(PDF_BYTES, MediaType.APPLICATION_PDF));

        byte[] result = service.generatePublicCourtListPdf(COURT_CENTRE_ID, START_DATE, END_DATE);

        assertThat(result).isEqualTo(PDF_BYTES);
        server.verify();
    }

    @Test
    void generatePublicCourtListPdf_throws_whenProgressionReturnsEmpty() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("courtlist")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.generatePublicCourtListPdf(COURT_CENTRE_ID, START_DATE, END_DATE))
                .isInstanceOf(PublicCourtListException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void generatePublicCourtListPdf_throws_whenProgressionReturnsNull() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("courtlist")))
                .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.generatePublicCourtListPdf(COURT_CENTRE_ID, START_DATE, END_DATE))
                .isInstanceOf(PublicCourtListException.class)
                .hasMessageContaining("empty");
    }
}
