package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.cp.models.transformed.CourtListDocument;

import java.net.URI;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaTHService {

    private final RestTemplate restTemplate;

    @Value("${cath.base-url:https://spnl-apim-int-gw.cpp.nonlive}")
    private String cathBaseUrl;

    @Value("${cath.endpoint:/courtlistpublisher/publication}")
    private String cathEndpoint;

    public void sendCourtListToCaTH(CourtListDocument courtListDocument) {
        try {
            log.info("Sending court list document to CaTH endpoint: {}", cathBaseUrl + cathEndpoint);

            URI uri = URI.create(cathBaseUrl + cathEndpoint);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<CourtListDocument> requestEntity = new HttpEntity<>(courtListDocument, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    uri,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            log.info("Successfully sent court list document to CaTH. Response status: {}", response.getStatusCode());
        } catch (Exception e) {
            log.error("Error sending court list document to CaTH endpoint: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send court list document to CaTH: " + e.getMessage(), e);
        }
    }
}
