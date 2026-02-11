package uk.gov.hmcts.cp.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.cp.domain.DtsMeta;

/**
 * Court list publisher used in integration tests. Performs a real HTTP POST to the CaTH URL
 * (WireMock), so integration tests can stub success or failure and assert on DB state.
 */
@Component
@Primary
@Profile("integration")
@Slf4j
public class IntegrationCourtListPublisher implements CourtListPublisher {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String cathBaseUrl;
    private final String cathEndpoint;

    public IntegrationCourtListPublisher(
            @Value("${cath.base-url:}") String cathBaseUrl,
            @Value("${cath.endpoint:/courtlistpublisher/publication}") String cathEndpoint) {
        this.cathBaseUrl = cathBaseUrl != null ? cathBaseUrl.stripTrailing() : "";
        this.cathEndpoint = cathEndpoint != null && cathEndpoint.startsWith("/") ? cathEndpoint : "/" + cathEndpoint;
    }

    @Override
    public int publish(String payload, DtsMeta metadata) {
        String url = cathBaseUrl + cathEndpoint;
        log.info("IntegrationCourtListPublisher: POST to {}", url);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var response = restTemplate.exchange(
                url,
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                String.class);
        int status = response.getStatusCode().value();
        if (!response.getStatusCode().is2xxSuccessful()) {
            log.warn("CaTH returned non-2xx: {}", status);
            throw new RuntimeException("CaTH returned " + status + ": " + response.getBody());
        }
        return status;
    }
}
