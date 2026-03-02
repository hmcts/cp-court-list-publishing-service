package uk.gov.hmcts.cp.services.publiccourtlist;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.json.Json;
import jakarta.json.JsonObject;
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
import uk.gov.hmcts.cp.config.ObjectMapperConfig;
import uk.gov.hmcts.cp.services.DocumentGeneratorClient;

import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;

/**
 * Generates the public court list PDF by calling the court list data API directly
 * (the same backend that progression used) and then the document generator.
 * Progression is not used; this allows progression to remove its public court list logic.
 */
@Service
@ConditionalOnProperty(name = "public-court-list.enabled", havingValue = "true")
public class PublicCourtListService {

    private static final Logger LOG = LoggerFactory.getLogger(PublicCourtListService.class);
    private static final String LIST_ID_PUBLIC = "PUBLIC";
    private static final String TEMPLATE_PUBLIC_COURT_LIST = "PublicCourtList";
    private static final String KEY_TEMPLATE_NAME = "templateName";
    /** Accept header for public court list JSON (same contract as the court list data API). */
    private static final String ACCEPT_COURT_LIST_JSON = "application/vnd.progression.search.court.list+json";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final RestTemplate restTemplate;
    private final String courtListDataBaseUrl;
    private final String courtListDataPath;
    private final DocumentGeneratorClient documentGeneratorClient;

    public PublicCourtListService(
            @Qualifier("publicCourtListRestTemplate") final RestTemplate restTemplate,
            @Value("${common-platform-query-api.base-url}") final String courtListDataBaseUrl,
            @Value("${public-court-list.court-list-data.path}") final String courtListDataPath,
            final DocumentGeneratorClient documentGeneratorClient) {
        this.restTemplate = restTemplate;
        this.courtListDataBaseUrl = courtListDataBaseUrl.endsWith("/") ? courtListDataBaseUrl : courtListDataBaseUrl + "/";
        this.courtListDataPath = courtListDataPath.startsWith("/") ? courtListDataPath.substring(1) : courtListDataPath;
        this.documentGeneratorClient = documentGeneratorClient;
    }

    public byte[] generatePublicCourtListPdf(final String courtCentreId, final LocalDate startDate, final LocalDate endDate) {
        LOG.info("Generating public court list PDF for courtCentreId={}, startDate={}, endDate={}",
                sanitizeForLog(courtCentreId), startDate, endDate);

        Map<String, Object> payload = fetchCourtListPayload(courtCentreId, startDate, endDate);
        if (payload == null || payload.isEmpty()) {
            throw new PublicCourtListException("Court list data API returned empty payload");
        }

        String templateName = payload.containsKey(KEY_TEMPLATE_NAME)
                ? String.valueOf(payload.get(KEY_TEMPLATE_NAME))
                : TEMPLATE_PUBLIC_COURT_LIST;

        JsonObject payloadJson = mapToJsonObject(payload);
        byte[] pdf;
        try {
            pdf = documentGeneratorClient.generatePdf(payloadJson, templateName);
        } catch (IOException e) {
            throw new PublicCourtListException(e.getMessage(), e);
        }

        LOG.info("Public court list PDF generated for courtCentreId={}, size={} bytes",
                sanitizeForLog(courtCentreId), pdf.length);
        return pdf;
    }

    /**
     * Fetches public court list payload from the court list data API (same backend contract
     * that progression used). Progression is not involved; this call can replace progression's role.
     */
    private Map<String, Object> fetchCourtListPayload(final String courtCentreId, final LocalDate startDate, final LocalDate endDate) {
        String url = UriComponentsBuilder.fromUriString(courtListDataBaseUrl + courtListDataPath)
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
            LOG.error("Court list data API call failed for courtCentreId={}", sanitizeForLog(courtCentreId), e);
            throw new PublicCourtListException("Failed to fetch court list: " + e.getMessage(), e);
        }
    }

    private static JsonObject mapToJsonObject(final Map<String, Object> map) {
        try {
            String json = ObjectMapperConfig.getObjectMapper().writeValueAsString(map);
            return Json.createReader(new StringReader(json)).readObject();
        } catch (JsonProcessingException e) {
            throw new PublicCourtListException("Failed to convert court list payload to JSON", e);
        }
    }

    private static String sanitizeForLog(final String value) {
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
}
