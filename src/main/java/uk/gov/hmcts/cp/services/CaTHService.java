package uk.gov.hmcts.cp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import uk.gov.hmcts.cp.config.ObjectMapperConfig;
import uk.gov.hmcts.cp.domain.DtsMeta;
import uk.gov.hmcts.cp.models.transformed.CourtListDocument;
import uk.gov.hmcts.cp.openapi.model.CourtListType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaTHService {

    record CathHeaderInfo(String cathCourtListType, String sensitivity){}


    private static final Map<CourtListType, CathHeaderInfo> COURT_LIST_MAPPINGS = ImmutableMap.of(
            CourtListType.ONLINE_PUBLIC, new CathHeaderInfo("MAGISTRATES_PUBLIC_LIST", "PUBLIC"),
            CourtListType.STANDARD, new CathHeaderInfo("MAGISTRATES_STANDARD_LIST", "CLASSIFIED"));

    @Autowired
    private final CourtListPublisher caTHPublisher;

    private static final ObjectMapper objectMapper = ObjectMapperConfig.getObjectMapper();

    public void sendCourtListToCaTH(CourtListDocument courtListDocument, final CourtListType courtListType) {
        try {
            log.info("Sending court list document to CaTH endpoint");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            final String courtIdFromRefData = courtListDocument.getCourtIdNumeric() != null && !courtListDocument.getCourtIdNumeric().isBlank()
                    ? courtListDocument.getCourtIdNumeric()
                    : "0";

            final CathHeaderInfo cathListInfo = COURT_LIST_MAPPINGS.get(courtListType);

            if(cathListInfo == null) {
                throw new IllegalStateException("Unsupported court list type "+courtListType);
            }

            final Instant now = Instant.now();
            final DtsMeta dtsMeta = DtsMeta.builder()
                    .provenance("COMMON_PLATFORM")
                    .type("LIST")
                    .listType(cathListInfo.cathCourtListType())
                    .courtId(courtIdFromRefData)
                    .contentDate(now.toString())
                    .language("ENGLISH")
                    .sensitivity(cathListInfo.sensitivity())
                    .displayFrom(now.toString())
                    .displayTo(now.plus(7, ChronoUnit.DAYS).toString())
                    .build();

            final Integer res = caTHPublisher.publish(objectMapper.writeValueAsString(courtListDocument), dtsMeta);

            log.info("Successfully sent court list document to CaTH. Response status: {}", res);
        } catch (Exception e) {
            log.error("Error sending court list document to CaTH endpoint: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send court list document to CaTH: " + e.getMessage(), e);
        }
    }
}
