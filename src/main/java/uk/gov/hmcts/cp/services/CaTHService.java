package uk.gov.hmcts.cp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import uk.gov.hmcts.cp.domain.DtsMeta;
import uk.gov.hmcts.cp.models.transformed.CourtListDocument;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaTHService {

    private final CaTHPublisher cathPublisher;

    ObjectMapper objectMapper = new ObjectMapper();

    public void sendCourtListToCaTH(CourtListDocument courtListDocument) {
        try {
            log.info("Sending court list document to CaTH endpoint");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            final DtsMeta dtsMeta = DtsMeta.builder()
                    .provenance("COMMON_PLATFORM")
                    .type("LIST")
                    .listType("MAGISTRATES_PUBLIC_ADULT_COURT_LIST_DAILY")
                    .courtId("0")// This should be court ID, a 3 digit string from refData
                    .contentDate("2024-03-27T12:39:41.362Z")
                    .language("ENGLISH")
                    .sensitivity("PUBLIC")// Thi sneeds to be dynamic along with other params here
                    .displayFrom("2026-01-08T12:39:41.362Z")
                    .displayTo("2026-01-11T13:39:41.362Z")
                    .build();

            final Integer res = cathPublisher.publish(objectMapper.writeValueAsString(courtListDocument), dtsMeta);

            log.info("Successfully sent court list document to CaTH. Response status: {}", res);
        } catch (Exception e) {
            log.error("Error sending court list document to CaTH endpoint: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send court list document to CaTH: " + e.getMessage(), e);
        }
    }
}
