package uk.gov.hmcts.cp.services.crown;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;
import uk.gov.hmcts.cp.domain.DtsMeta;
import uk.gov.hmcts.cp.models.crown.CrownListPayload;
import uk.gov.hmcts.cp.services.AzureBlobService;
import uk.gov.hmcts.cp.services.CaTHService;
import uk.gov.hmcts.cp.services.CourtListPublisher;
import uk.gov.hmcts.cp.services.JsonSchemaValidatorService;
import uk.gov.hmcts.cp.services.StandardCourtListTransformationService;
import uk.gov.hmcts.cp.services.sanitization.DocumentSanitizer;
import uk.gov.hmcts.cp.services.sanitization.HtmlStrippingSanitizer;
import uk.gov.hmcts.cp.services.sanitization.WafPatternSanitizer;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static uk.gov.hmcts.cp.config.AppConstant.ALERT_PATTERN;

/**
 * Publishes one crown court list to CaTH as a PDDA list ({@code CROWN_DAILY_PDDA_LIST},
 * {@code CROWN_FIRM_PDDA_LIST} or {@code CROWN_ADVANCE_PDDA_LIST}).
 *
 * <ol>
 *   <li>Fetches listing's crown list payload and cleans its text with the {@link DocumentSanitizer} used for the
 *   other CaTH lists (WAF-blocked sequences and HTML removed), before it is parsed; the court telephone from
 *   reference data is cleaned the same way.</li>
 *   <li>Builds the CaTH document and validates it against CaTH's schema.</li>
 *   <li>Publishes it with the same CaTH metadata as the other lists, sensitivity {@code PUBLIC}, displayed until
 *   23:59 UTC on the list's last day. A copy of the payload goes to blob storage (best effort).</li>
 * </ol>
 *
 * <p>The list is not sent when listing returned hearings but none of them can be published: an empty list would
 * replace the live one in CaTH because of a data problem. A list for which listing returned no hearings at all
 * is published (empty).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CrownListCaTHPublisher {

    static final String SENSITIVITY = "PUBLIC";
    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperConfig.getObjectMapper();

    private final CrownListDataClient dataClient;
    private final CrownListTransformer transformer;
    private final DocumentSanitizer documentSanitizer;
    private final WafPatternSanitizer wafPatternSanitizer;
    private final HtmlStrippingSanitizer htmlStrippingSanitizer;
    private final JsonSchemaValidatorService jsonSchemaValidatorService;
    private final CourtListPublisher courtListPublisher;
    private final Optional<AzureBlobService> azureBlobService;

    public void publish(CrownPublishRequest request) {
        CrownListPayload payload = parse(sanitize(dataClient.fetch(request)));

        CrownListTransformer.Result result = transformer.transform(withCleanTelephone(request), payload);
        if (result.hearingsListed() == 0 && result.hearingsInPayload() > 0) {
            throw new IllegalStateException("None of the " + result.hearingsInPayload() + " hearings listing returned"
                    + " can be published (restricted, outside the requested dates or without a case reference),"
                    + " so the list was not sent and the published list was left as it was");
        }

        String body = toJson(result);
        jsonSchemaValidatorService.validate(body, request.listType().schema());

        DtsMeta dtsMeta = dtsMeta(request, Boolean.TRUE.equals(payload.isWelsh()));
        log.info("Sending crown list to CaTH, {}, cathListType={}, courtId={}, hearings={}",
                request.describe(), dtsMeta.getListType(), dtsMeta.getCourtId(), result.hearingsListed());
        uploadToBlob(body, request);
        int status = courtListPublisher.publish(body, dtsMeta);
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("CaTH returned status " + status);
        }
        log.info("Crown list published to CaTH, {}, status={}", request.describe(), status);
    }

    private static DtsMeta dtsMeta(CrownPublishRequest request, boolean welsh) {
        return DtsMeta.builder()
                .provenance("COMMON_PLATFORM")
                .type("LIST")
                .listType(request.listType().cathListType())
                .courtId(request.courtIdNumeric())
                .contentDate(StandardCourtListTransformationService.toIsoDateTimeOrNull(request.startDate().toString()))
                .language(welsh ? "WELSH" : "ENGLISH")
                .sensitivity(SENSITIVITY)
                .displayFrom(Instant.now().toString())
                .displayTo(request.endDate().plusDays(1).atStartOfDay().minusMinutes(1).toInstant(ZoneOffset.UTC).toString())
                .build();
    }

    private CrownPublishRequest withCleanTelephone(CrownPublishRequest request) {
        String telephone = request.courtTelephone() == null ? null
                : htmlStrippingSanitizer.sanitize(wafPatternSanitizer.sanitize(request.courtTelephone()));
        return new CrownPublishRequest(request.courtListId(), request.courtCentreId(), request.listType(),
                request.startDate(), request.endDate(), request.userId(), request.courtIdNumeric(), telephone);
    }

    private void uploadToBlob(String body, CrownPublishRequest request) {
        azureBlobService.ifPresent(blobService -> {
            try {
                blobService.uploadJson(body, CaTHService.buildBlobName(request.courtListId()));
            } catch (Exception e) {
                log.error("Error {} uploading crown list CaTH payload to blob storage, publishing anyway, {}",
                        ALERT_PATTERN, request.describe(), e);
            }
        });
    }

    private String sanitize(String json) {
        try {
            return documentSanitizer.sanitize(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Crown list payload from listing is not JSON: " + e.getOriginalMessage(), e);
        }
    }

    private static CrownListPayload parse(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, CrownListPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to read crown list payload: " + e.getOriginalMessage(), e);
        }
    }

    private static String toJson(CrownListTransformer.Result result) {
        try {
            return OBJECT_MAPPER.writeValueAsString(result.document());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to write crown list document", e);
        }
    }
}
