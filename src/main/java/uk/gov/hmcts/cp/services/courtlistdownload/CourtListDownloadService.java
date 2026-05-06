package uk.gov.hmcts.cp.services.courtlistdownload;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.services.CourtListDataService;
import uk.gov.hmcts.cp.services.DocumentGeneratorClient;

import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
public class CourtListDownloadService {

    private static final Logger LOG = LoggerFactory.getLogger(CourtListDownloadService.class);
    private static final String TEMPLATE_PUBLIC_COURT_LIST = "PublicCourtList";
    private static final String TEMPLATE_BENCH_COURT_LIST = "BenchCourtList";
    private static final String TEMPLATE_ALPHABETICAL_COURT_LIST = "AlphabeticalCourtList";
    private static final String TEMPLATE_JUDGE_LIST = "JudgeList";
    private static final String TEMPLATE_USHERS_CROWN_COURT_LIST = "UshersCrownCourtList";
    private static final String TEMPLATE_USHERS_MAGISTRATE_COURT_LIST = "UshersMagistrateCourtList";
    private static final String KEY_TEMPLATE_NAME = "templateName";
    private static final String CONTENT_TYPE_PDF = "application/pdf";
    private static final String CONTENT_TYPE_WORD =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String PDF_FILENAME = "CourtList.pdf";
    private static final String WORD_FILENAME = "CourtList.docx";

    private static final Set<CourtListType> WORD_DOWNLOAD_TYPES = EnumSet.of(
            CourtListType.USHERS_CROWN,
            CourtListType.USHERS_MAGISTRATE);

    private static final Map<CourtListType, String> DEFAULT_TEMPLATE_BY_TYPE = new EnumMap<>(CourtListType.class);

    static {
        DEFAULT_TEMPLATE_BY_TYPE.put(CourtListType.PUBLIC, TEMPLATE_PUBLIC_COURT_LIST);
        DEFAULT_TEMPLATE_BY_TYPE.put(CourtListType.BENCH, TEMPLATE_BENCH_COURT_LIST);
        DEFAULT_TEMPLATE_BY_TYPE.put(CourtListType.ALPHABETICAL, TEMPLATE_ALPHABETICAL_COURT_LIST);
        DEFAULT_TEMPLATE_BY_TYPE.put(CourtListType.JUDGE, TEMPLATE_JUDGE_LIST);
        DEFAULT_TEMPLATE_BY_TYPE.put(CourtListType.USHERS_CROWN, TEMPLATE_USHERS_CROWN_COURT_LIST);
        DEFAULT_TEMPLATE_BY_TYPE.put(CourtListType.USHERS_MAGISTRATE, TEMPLATE_USHERS_MAGISTRATE_COURT_LIST);
    }

    private final CourtListDataService courtListDataService;
    private final DocumentGeneratorClient documentGeneratorClient;

    public CourtListDownloadService(
            final CourtListDataService courtListDataService,
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
        LOG.info("Generating court list document for type={}, courtCentreId={}, startDate={}, endDate={}",
                courtListType, sanitizeForLog(courtCentreId), startDate, endDate);

        Map<String, Object> payload = courtListDataService.getCourtListPayloadForDownload(
                courtListType, courtCentreId, courtRoomId, startDate, endDate, cjscppuid);
        if (payload == null || payload.isEmpty()) {
            throw new CourtListDownloadException("Court list data API returned empty payload");
        }

        String defaultTemplate = DEFAULT_TEMPLATE_BY_TYPE.getOrDefault(courtListType, TEMPLATE_PUBLIC_COURT_LIST);
        String templateName = payload.containsKey(KEY_TEMPLATE_NAME)
                ? String.valueOf(payload.get(KEY_TEMPLATE_NAME))
                : defaultTemplate;

        JsonObject payloadJson = mapToJsonObject(payload);
        boolean wantsWord = WORD_DOWNLOAD_TYPES.contains(courtListType);
        byte[] content;
        try {
            content = wantsWord
                    ? documentGeneratorClient.generateWord(payloadJson, templateName)
                    : documentGeneratorClient.generatePdf(payloadJson, templateName);
        } catch (IOException e) {
            throw new CourtListDownloadException(e.getMessage(), e);
        }

        LOG.info("Court list document generated for type={}, courtCentreId={}, size={} bytes",
                courtListType, sanitizeForLog(courtCentreId), content.length);

        return wantsWord
                ? new CourtListFileResult(content, CONTENT_TYPE_WORD, WORD_FILENAME)
                : new CourtListFileResult(content, CONTENT_TYPE_PDF, PDF_FILENAME);
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
