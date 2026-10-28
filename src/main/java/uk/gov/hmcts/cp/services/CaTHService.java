package uk.gov.hmcts.cp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import uk.gov.hmcts.cp.config.ObjectMapperConfig;
import uk.gov.hmcts.cp.domain.DtsMeta;
import uk.gov.hmcts.cp.models.transformed.CourtListDocument;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.services.crown.CrownListType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static uk.gov.hmcts.cp.config.AppConstant.ALERT_PATTERN;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaTHService {

    record CathHeaderInfo(String cathCourtListType, String sensitivity){}

    private static final String CATH_BLOB_SUFFIX = "-cath.json";
    /** Crown lists carry defendants' dates of birth and addresses, so they are not public. */
    private static final String CROWN_SENSITIVITY = "CLASSIFIED";
    private static final String CROWN_LANGUAGE = "ENGLISH";

    private static final Map<CourtListType, CathHeaderInfo> COURT_LIST_MAPPINGS = ImmutableMap.of(
            CourtListType.ONLINE_PUBLIC, new CathHeaderInfo("MAGISTRATES_PUBLIC_LIST", "PUBLIC"),
            CourtListType.STANDARD, new CathHeaderInfo("MAGISTRATES_STANDARD_LIST", "CLASSIFIED"));

    @Autowired
    private final CourtListPublisher caTHPublisher;

    private final Optional<AzureBlobService> azureBlobService;

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperConfig.getObjectMapper();

    public void sendCourtListToCaTH(CourtListDocument courtListDocument, final CourtListType courtListType, final LocalDate publishDate,
                                    String courtIdNumeric, Boolean isWelsh, UUID courtListId) {
        try {
            final String language = Boolean.TRUE.equals(isWelsh) ? "WELSH" : "ENGLISH";
            log.info("Sending court list document to CaTH endpoint, courtListType={}, language={}", courtListType, language);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            final String courtIdFromRefData = courtIdNumeric != null && !courtIdNumeric.isBlank()
                    ? courtIdNumeric
                    : "0";

            final CathHeaderInfo cathListInfo = COURT_LIST_MAPPINGS.get(courtListType);

            if(cathListInfo == null) {
                throw new IllegalStateException("Unsupported court list type "+courtListType);
            }

            log.info("CaTH list type mapping resolved: courtListType={} -> cathListType={}, sensitivity={}",
                    courtListType, cathListInfo.cathCourtListType(), cathListInfo.sensitivity());

            final Instant now = Instant.now();
            final DtsMeta dtsMeta = DtsMeta.builder()
                    .provenance("COMMON_PLATFORM")
                    .type("LIST")
                    .listType(cathListInfo.cathCourtListType())
                    .courtId(courtIdFromRefData)
                    .contentDate(StandardCourtListTransformationService.toIsoDateTimeOrNull(publishDate.toString()))
                    .language(language)
                    .sensitivity(cathListInfo.sensitivity())
                    .displayFrom(now.toString())
                    .displayTo(getDisplayTo(publishDate))
                    .build();

            final String payload = OBJECT_MAPPER.writeValueAsString(courtListDocument);

            uploadPayloadToBlob(payload, courtListId);

            final Integer res = caTHPublisher.publish(payload, dtsMeta);

            log.info("Successfully sent court list document to CaTH, courtListType={}, language={}, status={}",
                    courtListType, language, res);
        } catch (Exception e) {
            log.error("Error {} sending court list document to CaTH endpoint: {}", ALERT_PATTERN, e.getMessage(), e);
            throw new RuntimeException("Failed to send court list document to CaTH: " + e.getMessage(), e);
        }
    }

    /**
     * Publishes a Crown list that has already been built and validated against its CaTH schema.
     *
     * @param payload        the JSON document for {@code listType}
     * @param startDate      first day the list covers (the content date)
     * @param endDate        last day the list covers; it is shown until the end of that day
     * @param courtIdNumeric the court's numeric id from reference data; {@code "0"} when it is not known
     */
    public void sendCrownListToCaTH(final String payload, final CrownListType listType, final LocalDate startDate,
                                   final LocalDate endDate, final String courtIdNumeric, final UUID courtListId) {
        try {
            log.info("Sending Crown list to CaTH endpoint, cathListType={}", listType.cathListType());

            final String courtIdFromRefData = courtIdNumeric != null && !courtIdNumeric.isBlank() ? courtIdNumeric : "0";
            final DtsMeta dtsMeta = DtsMeta.builder()
                    .provenance("COMMON_PLATFORM")
                    .type("LIST")
                    .listType(listType.cathListType())
                    .courtId(courtIdFromRefData)
                    .contentDate(StandardCourtListTransformationService.toIsoDateTimeOrNull(startDate.toString()))
                    .language(CROWN_LANGUAGE)
                    .sensitivity(CROWN_SENSITIVITY)
                    .displayFrom(Instant.now().toString())
                    .displayTo(getDisplayTo(endDate))
                    .build();

            uploadPayloadToBlob(payload, courtListId);

            final Integer res = caTHPublisher.publish(payload, dtsMeta);

            log.info("Successfully sent Crown list to CaTH, cathListType={}, status={}", listType.cathListType(), res);
        } catch (Exception e) {
            log.error("Error {} sending Crown list to CaTH endpoint: {}", ALERT_PATTERN, e.getMessage(), e);
            throw new RuntimeException("Failed to send Crown list to CaTH: " + e.getMessage(), e);
        }
    }

    private void uploadPayloadToBlob(String payload, UUID courtListId) {
        azureBlobService.ifPresentOrElse(
            blobService -> {
                try {
                    String blobName = buildBlobName(courtListId);
                    blobService.uploadJson(payload, blobName);
                } catch (Exception e) {
                    log.error("Error {} uploading CaTH payload to blob storage, continuing with publish", ALERT_PATTERN, e);
                }
            },
            () -> log.debug("Azure Blob Service not available, skipping payload upload")
        );
    }

    public static String buildBlobName(UUID courtListId) {
        return courtListId + CATH_BLOB_SUFFIX;
    }

    static @NotNull String getDisplayTo(final LocalDate publishDate) {
        return publishDate.plusDays(1).atStartOfDay().minusMinutes(1).toInstant(ZoneOffset.UTC).toString();
    }
}
