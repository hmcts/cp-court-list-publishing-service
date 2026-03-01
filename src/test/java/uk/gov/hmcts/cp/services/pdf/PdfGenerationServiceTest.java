package uk.gov.hmcts.cp.services.pdf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PdfGenerationServiceTest {

    private static final String BASE_URL = "https://docgen.example";
    private static final byte[] PDF_BYTES = new byte[]{1, 2, 3};
    private static final String TEMPLATE_NAME = "PublicCourtList";
    private static final Map<String, Object> PAYLOAD = Map.of("key", "value");

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private PdfGenerationService service;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new PdfGenerationService(restTemplate, BASE_URL);
    }

    @Test
    void generatePdf_returnsPdf_whenCallSucceeds() {
        server.expect(requestTo(BASE_URL + "/generate-pdf"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(TEMPLATE_NAME)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("payload")))
                .andRespond(withSuccess(PDF_BYTES, MediaType.APPLICATION_PDF));

        byte[] result = service.generatePdf(TEMPLATE_NAME, PAYLOAD);

        assertThat(result).isEqualTo(PDF_BYTES);
        server.verify();
    }

    @Test
    void generatePdf_acceptsBaseUrlWithTrailingSlash() {
        service = new PdfGenerationService(restTemplate, BASE_URL + "/");
        server.expect(requestTo(BASE_URL + "/generate-pdf"))
                .andRespond(withSuccess(PDF_BYTES, MediaType.APPLICATION_PDF));

        byte[] result = service.generatePdf(TEMPLATE_NAME, PAYLOAD);

        assertThat(result).isEqualTo(PDF_BYTES);
        server.verify();
    }

    @Test
    void generatePdf_throws_whenResponseEmpty() {
        server.expect(requestTo(BASE_URL + "/generate-pdf"))
                .andRespond(withSuccess(new byte[0], MediaType.APPLICATION_PDF));

        assertThatThrownBy(() -> service.generatePdf(TEMPLATE_NAME, PAYLOAD))
                .isInstanceOf(PdfGenerationException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void generatePdf_throws_whenServerErrors() {
        server.expect(requestTo(BASE_URL + "/generate-pdf"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> service.generatePdf(TEMPLATE_NAME, PAYLOAD))
                .isInstanceOf(PdfGenerationException.class)
                .hasMessageContaining("PDF generation failed");
    }
}
