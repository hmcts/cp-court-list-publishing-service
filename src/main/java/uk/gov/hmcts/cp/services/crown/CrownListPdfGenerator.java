package uk.gov.hmcts.cp.services.crown;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.services.CourtListDataService;
import uk.gov.hmcts.cp.services.DocumentGeneratorClient;
import uk.gov.hmcts.cp.services.PdfGenerationService;
import uk.gov.hmcts.cp.services.TemplateInfo;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.UUID;

/**
 * Generates the PDF of a crown court list and uploads it to blob storage as {@code {courtListId}.pdf}, as
 * {@code CourtListPublishAndPDFGenerationTask} does for the other lists. Only FINAL (daily list) and FIRM lists
 * have a template; WARN has none, so it gets no PDF.
 *
 * <p>The PDF is rendered from listing's daily list payload ({@code dailylistpayload}, Accept
 * {@code application/vnd.listing.search.daily.list.payload+json}), the same payload and templates as the crown
 * court download. The payload's {@code isWelsh} flag picks the Welsh template.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "azure.storage.enabled", havingValue = "true", matchIfMissing = false)
public class CrownListPdfGenerator {

    static final String IS_WELSH = "isWelsh";
    private static final Map<CrownListType, TemplateInfo> TEMPLATES = Map.of(
            CrownListType.DAILY, new TemplateInfo("CrownDailyList", "CrownDailyListWelsh"),
            CrownListType.FIRM, new TemplateInfo("CrownFirmList", "CrownFirmListWelsh"));

    private final CourtListDataService courtListDataService;
    private final DocumentGeneratorClient documentGeneratorClient;
    private final PdfGenerationService pdfGenerationService;

    /** Whether the list type has a PDF template (FINAL and FIRM do; WARN does not). */
    public static boolean hasPdf(CrownListType listType) {
        return TEMPLATES.containsKey(listType);
    }

    /** The template for the list type in the given language. */
    static String templateName(CrownListType listType, boolean welsh) {
        TemplateInfo templates = TEMPLATES.get(listType);
        if (templates == null) {
            throw new IllegalArgumentException("No PDF template for crown list type " + listType.courtListType());
        }
        return welsh ? templates.welshTemplate() : templates.englishTemplate();
    }

    /**
     * Renders the list's PDF and uploads it.
     *
     * @return the file id of the uploaded PDF (the court list id)
     */
    public UUID generateAndUpload(CrownPublishRequest request) {
        if (!hasPdf(request.listType())) {
            throw new IllegalArgumentException("No PDF template for crown list type " + request.listType().courtListType());
        }
        String payloadJson = courtListDataService.getCrownCourtDailyListPayload(
                request.listType().courtListType(), request.courtCentreId().toString(), null,
                request.startDate(), request.endDate(), request.userId(), false);
        JsonObject payload = parse(payloadJson);
        boolean welsh = payload.get(IS_WELSH) == JsonValue.TRUE;
        String template = templateName(request.listType(), welsh);

        byte[] pdf;
        try {
            pdf = documentGeneratorClient.generatePdf(payload, template);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render the crown list PDF with template " + template
                    + ": " + e.getMessage(), e);
        }
        UUID fileId = pdfGenerationService.uploadPdfBytes(pdf, request.courtListId());
        log.info("Crown list PDF uploaded, {}, template={}, size={} bytes, courtListId={}",
                request.describe(), template, pdf.length, request.courtListId());
        return fileId;
    }

    private static JsonObject parse(String json) {
        try (JsonReader reader = Json.createReader(new StringReader(json))) {
            return reader.readObject();
        } catch (RuntimeException e) {
            throw new IllegalStateException("Listing returned a crown list payload that is not a JSON object: "
                    + e.getMessage(), e);
        }
    }
}
