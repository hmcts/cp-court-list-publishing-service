package uk.gov.hmcts.cp.services.courtlistdownload;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;
import uk.gov.hmcts.cp.services.CourtListDataService;
import uk.gov.hmcts.cp.services.DocumentGeneratorClient;

import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.util.Map;

@Service
public class CourtListDownloadService {

    private static final Logger LOG = LoggerFactory.getLogger(CourtListDownloadService.class);
    private static final String TEMPLATE_PUBLIC_COURT_LIST = "PublicCourtList";
    private static final String KEY_TEMPLATE_NAME = "templateName";

    private final CourtListDataService courtListDataService;
    private final DocumentGeneratorClient documentGeneratorClient;

    public CourtListDownloadService(
            final CourtListDataService courtListDataService,
            final DocumentGeneratorClient documentGeneratorClient) {
        this.courtListDataService = courtListDataService;
        this.documentGeneratorClient = documentGeneratorClient;
    }

    public byte[] generatePublicCourtListPdf(final String courtCentreId, final LocalDate startDate, final LocalDate endDate) {
        LOG.info("Generating public court list PDF for courtCentreId={}, startDate={}, endDate={}",
                sanitizeForLog(courtCentreId), startDate, endDate);

        Map<String, Object> payload = courtListDataService.getPublicCourtListPayload(courtCentreId, startDate, endDate);
        if (payload == null || payload.isEmpty()) {
            throw new CourtListDownloadException("Court list data API returned empty payload");
        }

        String templateName = payload.containsKey(KEY_TEMPLATE_NAME)
                ? String.valueOf(payload.get(KEY_TEMPLATE_NAME))
                : TEMPLATE_PUBLIC_COURT_LIST;

        JsonObject payloadJson = mapToJsonObject(payload);
        byte[] pdf;
        try {
            pdf = documentGeneratorClient.generatePdf(payloadJson, templateName);
        } catch (IOException e) {
            throw new CourtListDownloadException(e.getMessage(), e);
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
            throw new CourtListDownloadException("Failed to convert court list payload to JSON", e);
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
