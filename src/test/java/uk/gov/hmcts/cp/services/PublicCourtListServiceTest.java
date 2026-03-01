package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.cp.services.pdf.PdfGenerationException;
import uk.gov.hmcts.cp.services.pdf.PdfGenerationService;
import uk.gov.hmcts.cp.services.publiccourtlist.PublicCourtListException;
import uk.gov.hmcts.cp.services.publiccourtlist.PublicCourtListService;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class PublicCourtListServiceTest {

    private static final String COURT_CENTRE_ID = "f8254db1-1683-483e-afb3-b87fde5a0a26";
    private static final LocalDate START_DATE = LocalDate.of(2026, 2, 27);
    private static final LocalDate END_DATE = LocalDate.of(2026, 2, 27);
    private static final byte[] PDF_BYTES = new byte[]{1, 2, 3};
    private static final String PROGRESSION_URL = "https://progression.example/";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    @Mock
    private PdfGenerationService pdfGenerationService;
    private PublicCourtListService service;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new PublicCourtListService(restTemplate, PROGRESSION_URL, pdfGenerationService);
    }

    @Test
    void generatePublicCourtListPdf_returnsPdf_whenProgressionSucceedsAndPdfServiceReturnsPdf() {
        String courtListJson = "{\"templateName\":\"PublicCourtList\",\"listType\":\"public\",\"courtCentreName\":\"Test Court\"}";
        server.expect(requestTo(org.hamcrest.Matchers.containsString("courtlist")))
                .andRespond(withSuccess(courtListJson, MediaType.parseMediaType("application/vnd.progression.search.court.list+json")));
        when(pdfGenerationService.generatePdf(eq("PublicCourtList"), any(Map.class))).thenReturn(PDF_BYTES);

        byte[] result = service.generatePublicCourtListPdf(COURT_CENTRE_ID, START_DATE, END_DATE);

        assertThat(result).isEqualTo(PDF_BYTES);
        server.verify();
        verify(pdfGenerationService).generatePdf(eq("PublicCourtList"), any(Map.class));
    }

    @Test
    void generatePublicCourtListPdf_usesDefaultTemplate_whenPayloadHasNoTemplateName() {
        String courtListJson = "{\"listType\":\"public\"}";
        server.expect(requestTo(org.hamcrest.Matchers.containsString("courtlist")))
                .andRespond(withSuccess(courtListJson, MediaType.parseMediaType("application/vnd.progression.search.court.list+json")));
        when(pdfGenerationService.generatePdf(eq("PublicCourtList"), any(Map.class))).thenReturn(PDF_BYTES);

        byte[] result = service.generatePublicCourtListPdf(COURT_CENTRE_ID, START_DATE, END_DATE);

        assertThat(result).isEqualTo(PDF_BYTES);
        verify(pdfGenerationService).generatePdf(eq("PublicCourtList"), any(Map.class));
    }

    @Test
    void generatePublicCourtListPdf_throwsPublicCourtListException_whenPdfGenerationServiceThrows() {
        String courtListJson = "{\"templateName\":\"PublicCourtList\"}";
        server.expect(requestTo(org.hamcrest.Matchers.containsString("courtlist")))
                .andRespond(withSuccess(courtListJson, MediaType.parseMediaType("application/vnd.progression.search.court.list+json")));
        when(pdfGenerationService.generatePdf(any(String.class), any(Map.class))).thenThrow(new PdfGenerationException("Document generator returned empty PDF"));

        assertThatThrownBy(() -> service.generatePublicCourtListPdf(COURT_CENTRE_ID, START_DATE, END_DATE))
                .isInstanceOf(PublicCourtListException.class)
                .hasMessageContaining("Document generator returned empty PDF");
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
