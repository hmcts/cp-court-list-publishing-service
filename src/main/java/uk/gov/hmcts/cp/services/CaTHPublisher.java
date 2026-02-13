package uk.gov.hmcts.cp.services;

import uk.gov.hmcts.cp.domain.DtsMeta;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaTHPublisher implements CourtListPublisher {

    private final PublishingService publishingService;
    private final AzureIdentityService azureIdentityService;

    @Override
    public int publish(final String payload, final DtsMeta metadata) {
        log.info("Attempting to fetch local token...");
        String localToken;
        try {
            localToken = azureIdentityService.getTokenFromLocalClientSecretCredentials();
            if (localToken == null) {
                throw new RuntimeException("Local token fetch returned null");
            }
            log.info("Successfully fetched local token");
        } catch (Exception e) {
            log.error("Failed to fetch local token: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch local token: " + e.getMessage(), e);
        }

        log.info("Attempting to fetch remote token...");
        try {
            String remoteToken = azureIdentityService.getTokenFromRemoteClientSecretCredentials();
            if (remoteToken == null) {
                throw new RuntimeException("Remote token fetch returned null");
            }
            log.info("Successfully fetched remote token");
        } catch (Exception e) {
            log.error("Failed to fetch remote token: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch remote token: " + e.getMessage(), e);
        }

        log.info("Publishing payload to CaTH");
        int statusCode = publishingService.sendData(payload, metadata);
        log.info("Successfully published to CaTH with status: {}", statusCode);
        return statusCode;
    }
}
