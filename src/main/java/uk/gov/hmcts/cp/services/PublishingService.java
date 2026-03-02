package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.cp.config.DtsAzureConfig;
import uk.gov.hmcts.cp.domain.DtsMeta;

/**
 * Service for publishing data to the Publishing Hub.
 *
 * <p>This service provides methods to send data to the Publishing Hub API,
 * specifically using the V2 endpoint with Azure managed identity authentication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PublishingService {

    private static final String APIM_LOGGER = "APIM {} called and received status response: {}";
    private static final String BEARER_TOKEN = "Bearer %s";
    private static final String EXTERNAL_SERVICE_ACCESS_TOKEN = "external-service-access-token";
    private static final String OCP_APIM_TRACE = "Ocp-Apim-Trace";
    private static final String TRUE = "true";

    private final DtsAzureConfig applicationParameters;
    private final RestTemplate restTemplate;
    private final AzureIdentityService azureIdentityService;

    /**
     * Sends data to the Publishing Hub V2 endpoint using Azure managed identity authentication.
     *
     * @param payload the JSON payload to send
     * @param metadata the metadata for the publication
     * @return the HTTP status code of the response (2xx only; otherwise throws)
     * @throws RuntimeException if the response status is not 2xx
     */
    public Integer sendData(final String payload, final DtsMeta metadata) {
        log.info("TODO: remove this --- CaTH publish request payload: {}", payload);
        log.info("TODO: remove this --- CaTH publish request metadata: {}", metadata);

        String url = applicationParameters.getAzureLocalDtsApimUrl();
        HttpHeaders headers = buildHeaders(
                azureIdentityService.getTokenFromLocalClientSecretCredentials(),
                azureIdentityService.getTokenFromRemoteClientSecretCredentials(),
                metadata
        );
        HttpEntity<String> requestEntity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            int status = response.getStatusCode().value();
            String responseBody = response.getBody() != null ? response.getBody() : "";
            log.info("TODO: remove this --- CaTH publish response status: {}, body: {}", status, responseBody);
            log.info(APIM_LOGGER, url, status);

            if (status < 200 || status >= 300) {
                throw new RuntimeException("CaTH publish failed with HTTP status " + status + ": " + responseBody);
            }
            return status;
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            int status = e.getStatusCode().value();
            String responseBody = e.getResponseBodyAsString();
            log.info("TODO: remove this --- CaTH publish response status: {}, body: {}", status, responseBody);
            log.info(APIM_LOGGER, url, status);
            throw new RuntimeException("CaTH publish failed with HTTP status " + status + ": " + responseBody);
        }
    }

    private HttpHeaders buildHeaders(String localServiceAccessToken, String remoteServiceAccessToken, DtsMeta meta) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", String.format(BEARER_TOKEN, localServiceAccessToken));
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        headers.set(EXTERNAL_SERVICE_ACCESS_TOKEN, remoteServiceAccessToken);
        headers.set(OCP_APIM_TRACE, TRUE);
        headers.set("x-provenance", meta.getProvenance());
        headers.set("x-type", meta.getType());
        headers.set("x-list-type", meta.getListType());
        headers.set("x-court-id", meta.getCourtId());
        headers.set("x-content-date", meta.getContentDate());
        headers.set("x-language", meta.getLanguage());
        headers.set("x-sensitivity", meta.getSensitivity());
        headers.set("x-display-from", meta.getDisplayFrom());
        headers.set("x-display-to", meta.getDisplayTo());
        return headers;
    }
}
