package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.config.DtsAzureConfig;
import uk.gov.hmcts.cp.domain.DtsMeta;

import jakarta.ws.rs.core.Response;

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

    private final DtsAzureConfig applicationParameters;
    private final RestEasyClientService restEasyClientService;
    private final AzureIdentityService azureIdentityService;

    /**
     * Sends data to the Publishing Hub V2 endpoint using Azure managed identity authentication.
     * 
     * @param payload the JSON payload to send
     * @param metadata the metadata for the publication
     * @return the HTTP status code of the response
     */
    public Integer sendData(final String payload, final DtsMeta metadata) {
        log.warn("TODO: remove this --- CaTH publish request payload: {}", payload);
        log.warn("TODO: remove this --- CaTH publish request metadata: {}", metadata);

        final Response response = restEasyClientService.post(
                applicationParameters.getAzureLocalDtsApimUrl(),
                payload,
                azureIdentityService.getTokenFromLocalClientSecretCredentials(),
                azureIdentityService.getTokenFromRemoteClientSecretCredentials(),
                metadata
        );

        final int status = response.getStatus();
        final String responseBody = response.readEntity(String.class);
        log.warn("TODO: remove this --- CaTH publish response status: {}, body: {}", status, responseBody);
        log.info(APIM_LOGGER, applicationParameters.getAzureLocalDtsApimUrl(), status);
        return status;
    }
}
