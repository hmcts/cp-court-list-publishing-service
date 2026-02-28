package uk.gov.hmcts.cp.services.publiccourtlist;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@ConditionalOnProperty(name = "public-court-list.enabled", havingValue = "true")
public class PublicCourtListService {

    private static final Logger LOG = LoggerFactory.getLogger(PublicCourtListService.class);
    private static final String LIST_ID_PUBLIC = "PUBLIC";
    private static final String TEMPLATE_PUBLIC_COURT_LIST = "PublicCourtList";
    private static final String KEY_TEMPLATE_NAME = "templateName";
    private static final String ACCEPT_COURT_LIST_JSON = "application/vnd.progression.search.court.list+json";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    /** Sanitize user-provided values before logging to prevent log injection (CR/LF and control chars). */
    private static String sanitizeForLog(String value) {
        if (value == null) {
            return null;
        }
        String withoutCrLf = value.replace('\r', ' ').replace('\n', ' ');
        StringBuilder cleaned = new StringBuilder(withoutCrLf.length());
        for (int i = 0; i < withoutCrLf.length(); i++) {
            char c = withoutCrLf.charAt(i);
            if (!Character.isISOControl(c)) {
                cleaned.append(c);
            }
        }
        return cleaned.toString();
    }

    private final RestTemplate restTemplate;
    private final String progressionBaseUrl;
    private final String documentGeneratorBaseUrl;

    public PublicCourtListService(
            @Qualifier("publicCourtListRestTemplate") final RestTemplate restTemplate,
            @Value("${public-court-list.progression-api.base-url}") final String progressionBaseUrl,
            @Value("${public-court-list.document-generator.base-url}") final String documentGeneratorBaseUrl) {
        this.restTemplate = restTemplate;
        this.progressionBaseUrl = progressionBaseUrl.endsWith("/") ? progressionBaseUrl : progressionBaseUrl + "/";
        this.documentGeneratorBaseUrl = documentGeneratorBaseUrl.endsWith("/") ? documentGeneratorBaseUrl : documentGeneratorBaseUrl + "/";
    }

    public byte[] generatePublicCourtListPdf(final String courtCentreId, final LocalDate startDate, final LocalDate endDate) {
        LOG.info("Generating public court list PDF for courtCentreId={}, startDate={}, endDate={}",
                sanitizeForLog(courtCentreId), startDate, endDate);

        Map<String, Object> payload = fetchCourtListPayload(courtCentreId, startDate, endDate);
        if (payload == null || payload.isEmpty()) {
            throw new PublicCourtListException("Progression returned empty court list payload");
        }

        String templateName = payload.containsKey(KEY_TEMPLATE_NAME)
                ? String.valueOf(payload.get(KEY_TEMPLATE_NAME))
                : TEMPLATE_PUBLIC_COURT_LIST;
        byte[] pdf = generatePdf(payload, templateName);
        LOG.info("Public court list PDF generated for courtCentreId={}, size={} bytes",
                sanitizeForLog(courtCentreId), pdf.length);
        return pdf;
    }

    private Map<String, Object> fetchCourtListPayload(String courtCentreId, LocalDate startDate, LocalDate endDate) {
        String url = UriComponentsBuilder.fromUriString(progressionBaseUrl + "courtlist")
                .queryParam("listId", LIST_ID_PUBLIC)
                .queryParam("courtCentreId", courtCentreId)
                .queryParam("startDate", startDate.format(DATE_FORMAT))
                .queryParam("endDate", endDate.format(DATE_FORMAT))
                .queryParam("restricted", false)
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.parseMediaType(ACCEPT_COURT_LIST_JSON)));

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            return response.getBody();
        } catch (RestClientException e) {
            LOG.error("Progression API call failed for courtCentreId={}", sanitizeForLog(courtCentreId), e);
            throw new PublicCourtListException("Failed to fetch court list: " + e.getMessage(), e);
        }
    }

    private byte[] generatePdf(Map<String, Object> payload, String templateName) {
        String url = documentGeneratorBaseUrl + "generate-pdf";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new HashMap<>();
        body.put(KEY_TEMPLATE_NAME, templateName);
        body.put("payload", payload);

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), byte[].class);
            byte[] pdf = response.getBody();
            if (pdf == null || pdf.length == 0) {
                throw new PublicCourtListException("Document generator returned empty PDF");
            }
            return pdf;
        } catch (RestClientException e) {
            LOG.error("Document generator call failed for template={}", templateName, e);
            throw new PublicCourtListException("PDF generation failed: " + e.getMessage(), e);
        }
    }
}
