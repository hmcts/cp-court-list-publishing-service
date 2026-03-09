package uk.gov.hmcts.cp.services.sjp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.api.sjp.PublishSjpCourtListRequest;
import uk.gov.hmcts.cp.api.sjp.PublishSjpCourtListResponse;
import uk.gov.hmcts.cp.api.sjp.SjpListPayload;
import uk.gov.hmcts.cp.domain.DtsMeta;
import uk.gov.hmcts.cp.services.CourtListPublisher;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Handles publishing of SJP (Single Justice Procedure) court lists to CaTH.
 * Replicates PubHub flow: transform listPayload to CaTH payload, build metadata, POST to CaTH (APIM or stub in integration).
 */
@Service
public class SjpCourtListPublishService {

    private static final Logger LOG = LoggerFactory.getLogger(SjpCourtListPublishService.class);
    private static final String STATUS_ACCEPTED = "ACCEPTED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String PROVENANCE = "COMMON_PLATFORM";
    private static final String TYPE_LIST = "LIST";
    private static final String CATH_LIST_TYPE_PUBLIC = "SJP_PUBLIC_LIST";
    private static final String CATH_LIST_TYPE_PRESS = "SJP_PRESS_LIST";
    private static final String SENSITIVITY_PUBLIC = "PUBLIC";
    private static final String SENSITIVITY_CLASSIFIED = "CLASSIFIED";
    private static final String DOCUMENT_NAME_PUBLIC = "SJP Public list";
    private static final String DOCUMENT_NAME_PRESS = "SJP Press list";

    private final SjpToCathPayloadTransformer transformer;
    private final CourtListPublisher courtListPublisher;

    @Value("${cath.publishing-enabled:false}")
    private boolean cathPublishingEnabled;

    public SjpCourtListPublishService(
            SjpToCathPayloadTransformer transformer,
            CourtListPublisher courtListPublisher) {
        this.transformer = transformer;
        this.courtListPublisher = courtListPublisher;
    }

    /**
     * Publishes SJP court list to CaTH when listPayload is provided and CaTH is enabled.
     * Equivalent to PubHub: transform listPayload to PubhubMaster shape, build DtsMeta, POST to APIM.
     */
    public PublishSjpCourtListResponse publishSjpCourtList(PublishSjpCourtListRequest request) {
        String listType = request.getListType();
        LOG.info("SJP court list publish request for listType: {}", listType);

        if (request.getListPayload() == null) {
            return PublishSjpCourtListResponse.builder()
                    .status(STATUS_FAILED)
                    .listType(listType)
                    .message("listPayload is required to publish to CaTH")
                    .build();
        }

        SjpListPayload listPayload = request.getListPayload();
        if (listPayload.getReadyCases() == null || listPayload.getReadyCases().isEmpty()) {
            return PublishSjpCourtListResponse.builder()
                    .status(STATUS_ACCEPTED)
                    .listType(listType)
                    .message("listPayload has no readyCases; nothing to publish")
                    .build();
        }

        if (!cathPublishingEnabled) {
            LOG.warn("CaTH publishing is disabled; SJP list not sent to CaTH");
            return PublishSjpCourtListResponse.builder()
                    .status(STATUS_ACCEPTED)
                    .listType(listType)
                    .message("SJP court list publish request accepted (CaTH not configured)")
                    .build();
        }

        try {
            boolean isPressList = request.isSjpPressList();
            String documentName = isPressList ? DOCUMENT_NAME_PRESS : DOCUMENT_NAME_PUBLIC;
            String cathListType = isPressList ? CATH_LIST_TYPE_PRESS : CATH_LIST_TYPE_PUBLIC;
            String sensitivity = isPressList ? SENSITIVITY_CLASSIFIED : SENSITIVITY_PUBLIC;
            String language = request.getLanguage() != null && !request.getLanguage().isBlank()
                    ? request.getLanguage()
                    : "ENGLISH";

            String payload = transformer.transform(listPayload, documentName, isPressList);
            DtsMeta meta = buildSjpMeta(cathListType, sensitivity, language);

            int status = courtListPublisher.publish(payload, meta);
            LOG.info("SJP court list published to CaTH, listType={}, status={}", listType, status);

            return PublishSjpCourtListResponse.builder()
                    .status(STATUS_ACCEPTED)
                    .listType(listType)
                    .message("SJP court list published to CaTH")
                    .build();
        } catch (Exception e) {
            LOG.error("Failed to publish SJP court list to CaTH: {}", e.getMessage(), e);
            return PublishSjpCourtListResponse.builder()
                    .status(STATUS_FAILED)
                    .listType(listType)
                    .message("Failed to publish to CaTH: " + e.getMessage())
                    .build();
        }
    }

    private static DtsMeta buildSjpMeta(String listType, String sensitivity, String language) {
        Instant now = Instant.now();
        String contentDate = now.toString();
        String displayTo = now.plus(24, ChronoUnit.HOURS).toString();
        return DtsMeta.builder()
                .provenance(PROVENANCE)
                .type(TYPE_LIST)
                .listType(listType)
                .courtId("0")
                .contentDate(contentDate)
                .language(language)
                .sensitivity(sensitivity)
                .displayFrom(contentDate)
                .displayTo(displayTo)
                .build();
    }
}
