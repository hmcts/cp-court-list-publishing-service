package uk.gov.hmcts.cp.services;

import uk.gov.hmcts.cp.domain.DtsMeta;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaTHPublisher {

    private final PublishingService publishingService;
    private final AzureIdentityService azureIdentityService;


    public Integer publish(final String payload, final DtsMeta metadata) {
        Integer statusCode = HttpStatus.OK.value();
        String localToken = null;
        try {
            log.info("Attempting to fetch local token...");
            localToken = azureIdentityService.getTokenFromLocalClientSecretCredentials();
            if (localToken != null) {
                log.info("Successfully fetched local token");
            } else {
                log.warn("Local token fetch returned null");
            }
        } catch (Exception e) {
            log.error("Failed to fetch local token: " + e.getMessage(), e);
            statusCode = 500;
        }

        // Fetch remote token independently with try/catch
        String remoteToken = null;
        try {
            log.info("Attempting to fetch remote token...");
            remoteToken = azureIdentityService.getTokenFromRemoteClientSecretCredentials();
            if (remoteToken != null) {
                log.info("Successfully fetched remote token");
            } else {
                log.warn("Remote token fetch returned null");
            }
        } catch (Exception e) {
            log.error("Failed to fetch remote token: " + e.getMessage(), e);
            statusCode = 500;
        }

        if (HttpStatus.OK.value() == statusCode) {
            log.info("=========about to publish dummy pauload");
            statusCode = publishingService.sendData(payload, metadata);
            log.info("=========successfully published dummy pauload and status is {}", statusCode);
        } else {
            log.info("=========did not publish as token gen failed with status {}",  statusCode);
        }

        log.info("Test auth completed with status code: {}", statusCode);

        return statusCode;
    }
}
