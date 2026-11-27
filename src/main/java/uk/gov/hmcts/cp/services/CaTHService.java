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

            final DtsMeta dtsMeta = dtsMeta(cathListInfo.cathCourtListType(), cathListInfo.sensitivity(),
                    courtIdFromRefData, publishDate, publishDate, Boolean.TRUE.equals(isWelsh));

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

    /**
     * CaTH metadata for a list publication, built the same way for every list type: provenance
     * {@code COMMON_PLATFORM}, content date = the list's first day, displayed from now until 23:59 UTC on its last day.
     *
     * @param startDate first day the list covers
     * @param endDate   last day the list covers (the same day for a single-day list)
     */
    public static DtsMeta dtsMeta(String cathListType, String sensitivity, String courtId,
                                  LocalDate startDate, LocalDate endDate, boolean welsh) {
        return DtsMeta.builder()
                .provenance("COMMON_PLATFORM")
                .type("LIST")
                .listType(cathListType)
                .courtId(courtId)
                .contentDate(StandardCourtListTransformationService.toIsoDateTimeOrNull(startDate.toString()))
                .language(welsh ? "WELSH" : "ENGLISH")
                .sensitivity(sensitivity)
                .displayFrom(Instant.now().toString())
                .displayTo(getDisplayTo(endDate))
                .build();
    }

    public static String buildBlobName(UUID courtListId) {
        return courtListId + CATH_BLOB_SUFFIX;
    }

    static @NotNull String getDisplayTo(final LocalDate publishDate) {
        return publishDate.plusDays(1).atStartOfDay().minusMinutes(1).toInstant(ZoneOffset.UTC).toString();
    }
}
