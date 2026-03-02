package uk.gov.hmcts.cp.services.publiccourtlist;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;
import uk.gov.hmcts.cp.services.CourtListDataService;
import uk.gov.hmcts.cp.services.DocumentGeneratorClient;

import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "public-court-list.enabled", havingValue = "true")
public class PublicCourtListService {

    private static final Logger LOG = LoggerFactory.getLogger(PublicCourtListService.class);
    private static final String TEMPLATE_PUBLIC_COURT_LIST = "PublicCourtList";
    private static final String KEY_TEMPLATE_NAME = "templateName";

    private final CourtListDataService courtListDataService;
    private final DocumentGeneratorClient documentGeneratorClient;

    public PublicCourtListService(
            CourtListDataService courtListDataService,
            DocumentGeneratorClient documentGeneratorClient) {
        this.courtListDataService = courtListDataService;
        this.documentGeneratorClient = documentGeneratorClient;
    }

    public byte[] generatePublicCourtListPdf(final String courtCentreId, final LocalDate startDate, final LocalDate endDate) {
        LOG.info("Generating public court list PDF for courtCentreId={}, startDate={}, endDate={}",
                sanitizeForLog(courtCentreId), startDate, endDate);

        Map<String, Object> payload;
        try {
            payload = courtListDataService.getPublicCourtListPayload(courtCentreId, startDate, endDate);
        } catch (PublicCourtListException e) {
            String contextMsg = "Court list data failed for courtCentreId=" + sanitizeForLog(courtCentreId)
                    + ", startDate=" + startDate + ", endDate=" + endDate + ": " + e.getMessage();
            LOG.warn(contextMsg);
            throw new PublicCourtListException(contextMsg, e);
        } catch (IllegalStateException e) {
            String contextMsg = "Court list data API call failed for courtCentreId=" + sanitizeForLog(courtCentreId)
                    + ", startDate=" + startDate + ", endDate=" + endDate + ": " + e.getMessage();
            LOG.error(contextMsg, e);
            throw new PublicCourtListException(contextMsg, e);
        }
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
            String contextMsg = "Document generator failed for courtCentreId=" + sanitizeForLog(courtCentreId)
                    + ", template=" + templateName + ": " + e.getMessage();
            LOG.error(contextMsg, e);
            throw new PublicCourtListException(contextMsg, e);
        }

        LOG.info("Public court list PDF generated for courtCentreId={}, size={} bytes",
                sanitizeForLog(courtCentreId), pdf.length);
        return pdf;
    }

    private static JsonObject mapToJsonObject(final Map<String, Object> map) {
        try {
            String json = ObjectMapperConfig.getObjectMapper().writeValueAsString(map);
            return Json.createReader(new StringReader(json)).readObject();
        } catch (JsonProcessingException e) {
            LOG.error("Failed to convert court list payload to JSON", e);
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
