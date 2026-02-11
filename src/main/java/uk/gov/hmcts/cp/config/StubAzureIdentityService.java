package uk.gov.hmcts.cp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.services.AzureIdentityService;

/**
 * Stub Azure identity service for integration profile.
 * Returns fixed tokens so CaTHPublisher and PublishingService can call WireMock without real Azure credentials.
 */
@Service
@Primary
@Profile("integration")
@Slf4j
public class StubAzureIdentityService extends AzureIdentityService {

    private static final String STUB_TOKEN = "integration-test-token";

    public StubAzureIdentityService(DtsAzureConfig applicationParameters) {
        super(applicationParameters);
    }

    @Override
    public String getTokenFromLocalClientSecretCredentials() {
        log.debug("StubAzureIdentityService: returning stub local token (integration profile)");
        return STUB_TOKEN;
    }

    @Override
    public String getTokenFromRemoteClientSecretCredentials() {
        log.debug("StubAzureIdentityService: returning stub remote token (integration profile)");
        return STUB_TOKEN;
    }
}
