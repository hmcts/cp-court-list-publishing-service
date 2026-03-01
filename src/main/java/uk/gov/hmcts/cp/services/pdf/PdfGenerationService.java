package uk.gov.hmcts.cp.services.pdf;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Generic service to generate PDFs via the document generator API.
 * Can be reused by public court list and other PDF generation features.
 */
public class PdfGenerationService {

    private static final Logger LOG = LoggerFactory.getLogger(PdfGenerationService.class);
    private static final String TEMPLATE_NAME_KEY = "templateName";
    private static final String PAYLOAD_KEY = "payload";
    private static final String GENERATE_PDF_PATH = "generate-pdf";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PdfGenerationService(RestTemplate restTemplate, String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl == null || !baseUrl.endsWith("/") ? baseUrl : baseUrl.substring(0, baseUrl.length() - 1);
    }

    /**
     * Calls the document generator to produce a PDF from the given template and payload.
     *
     * @param templateName document generator template name
     * @param payload      data for the template (must be JSON-serialisable)
     * @return PDF bytes, never null or empty
     * @throws PdfGenerationException if the call fails or response is empty
     */
    public byte[] generatePdf(String templateName, Map<String, Object> payload) {
        String url = baseUrl + "/" + GENERATE_PDF_PATH;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new HashMap<>();
        body.put(TEMPLATE_NAME_KEY, templateName);
        body.put(PAYLOAD_KEY, payload);

        try {
            var response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), byte[].class);
            byte[] pdf = response.getBody();
            if (pdf == null || pdf.length == 0) {
                throw new PdfGenerationException("Document generator returned empty PDF");
            }
            return pdf;
        } catch (RestClientException e) {
            LOG.error("Document generator call failed for template={}", templateName, e);
            throw new PdfGenerationException("PDF generation failed: " + e.getMessage(), e);
        }
    }
}
