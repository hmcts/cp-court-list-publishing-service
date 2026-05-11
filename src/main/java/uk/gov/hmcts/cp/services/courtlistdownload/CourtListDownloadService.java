package uk.gov.hmcts.cp.services.courtlistdownload;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.services.CourtListDataService;
import uk.gov.hmcts.cp.services.DocumentGeneratorClient;

import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

@Service
public class CourtListDownloadService {

    private static final Logger LOG = LoggerFactory.getLogger(CourtListDownloadService.class);

    private static final String CONTENT_TYPE_PDF = "application/pdf";
    private static final String CONTENT_TYPE_WORD =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String PDF_FILENAME = "CourtList.pdf";
    private static final String WORD_FILENAME = "CourtList.docx";
    private static final String KEY_TEMPLATE_NAME = "templateName";

    private static final Set<CourtListType> WORD_DOWNLOAD_TYPES = EnumSet.of(
            CourtListType.USHERS_CROWN,
            CourtListType.USHERS_MAGISTRATE);

    // Set of list types we accept for download. Used only as a guard against unsupported types
    // (PRISON, ONLINE_PUBLIC, etc.); the actual template name comes from the listing payload.
    private static final Set<CourtListType> SUPPORTED_TYPES = EnumSet.of(
            CourtListType.PUBLIC,
            CourtListType.BENCH,
            CourtListType.STANDARD,
            CourtListType.ALPHABETICAL,
            CourtListType.JUDGE,
            CourtListType.USHERS_CROWN,
            CourtListType.USHERS_MAGISTRATE);

    private final CourtListDataService courtListDataService;
    private final DocumentGeneratorClient documentGeneratorClient;

    public CourtListDownloadService(final CourtListDataService courtListDataService,
                                    final DocumentGeneratorClient documentGeneratorClient) {
        this.courtListDataService = courtListDataService;
        this.documentGeneratorClient = documentGeneratorClient;
    }

    public CourtListFileResult generateCourtListDownload(final CourtListType courtListType,
                                                         final String courtCentreId,
                                                         final String courtRoomId,
                                                         final LocalDate startDate,
                                                         final LocalDate endDate,
                                                         final String cjscppuid) {
        if (!SUPPORTED_TYPES.contains(courtListType)) {
            throw new CourtListDownloadException("Unsupported court list type for download: " + courtListType);
        }
        final boolean wantsWord = WORD_DOWNLOAD_TYPES.contains(courtListType);

        LOG.info("Generating court list document for type={}, courtCentreId={}, startDate={}, endDate={}",
                courtListType, sanitizeForLog(courtCentreId), startDate, endDate);

        final String payloadJson = courtListDataService.getCourtListPayloadForDownload(
                courtListType, courtCentreId, courtRoomId, startDate, endDate, cjscppuid);

        final JsonObject payload;
        try (JsonReader reader = Json.createReader(new StringReader(payloadJson))) {
            payload = reader.readObject();
        } catch (Exception e) {
            throw new CourtListDownloadException("Failed to parse court list payload JSON: " + e.getMessage(), e);
        }

        // Listing's payload carries the templateName matching the templates registered in document-generator.
        // Use it directly so we stay aligned with whatever listing decides, no hardcoded mapping required.
        final String templateName = payload.containsKey(KEY_TEMPLATE_NAME) && !payload.isNull(KEY_TEMPLATE_NAME)
                ? payload.getString(KEY_TEMPLATE_NAME, null)
                : null;
        if (templateName == null || templateName.isBlank()) {
            throw new CourtListDownloadException(
                    "Listing payload for " + courtListType + " is missing required field 'templateName'");
        }

        final byte[] content;
        try {
            content = wantsWord
                    ? documentGeneratorClient.generateWord(payload, templateName)
                    : documentGeneratorClient.generatePdf(payload, templateName);
        } catch (IOException e) {
            throw new CourtListDownloadException("Failed to render court list document: " + e.getMessage(), e);
        }

        LOG.info("Court list document generated for type={}, template={}, courtCentreId={}, format={}, size={} bytes",
                courtListType, templateName, sanitizeForLog(courtCentreId), wantsWord ? "docx" : "pdf", content.length);

        return wantsWord
                ? new CourtListFileResult(content, CONTENT_TYPE_WORD, WORD_FILENAME)
                : new CourtListFileResult(content, CONTENT_TYPE_PDF, PDF_FILENAME);
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
