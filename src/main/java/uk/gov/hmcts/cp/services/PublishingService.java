package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.config.DtsAzureConfig;
import uk.gov.hmcts.cp.domain.Meta;

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
    public Integer sendData(final String payload, final Meta metadata) {
        final Response response = restEasyClientService.post(
                applicationParameters.getAzureLocalDtsApimUrl(),
                payload,
                azureIdentityService.getTokenFromLocalClientSecretCredentials(),
                azureIdentityService.getTokenFromRemoteClientSecretCredentials(),
                metadata
        );
        log.info(APIM_LOGGER, applicationParameters.getAzureLocalDtsApimUrl(), response.getStatus());
        return response.getStatus();
    }
}
