package uk.gov.hmcts.cp.services;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.core.util.Configuration;
import com.azure.identity.ManagedIdentityCredential;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import uk.gov.hmcts.cp.config.ApplicationParameters;

/**
 * Service for obtaining Azure managed identity tokens.
 * 
 * <p>This service provides methods to obtain access tokens from Azure
 * using managed identity credentials for both local and remote services.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AzureIdentityService {
    
    public static final String AZURE_CLIENT_ID = "AZURE_CLIENT_ID";
    public static final String AZURE_TENANT_ID = "AZURE_TENANT_ID";
    
    private final ApplicationParameters applicationParameters;

    /**
     * Gets a token from local client secret credentials.
     * 
     * @return the access token, or null if acquisition fails
     */
    public String getTokenFromLocalClientSecretCredentials() {
        String accessToken = null;
        
        try {
            final Configuration configuration = new Configuration();
            configuration.put(AZURE_CLIENT_ID, applicationParameters.getAzureLocalMiApimAuthClientId());
            configuration.put(AZURE_TENANT_ID, applicationParameters.getAzureLocalMiTenantId());
            final ManagedIdentityCredential managedIdentityCredential = new ManagedIdentityCredentialBuilder()
                    .configuration(configuration)
                    .build();
            final TokenRequestContext context = getTokenRequestContext();
            final Mono<String> accessTokenMono = managedIdentityCredential.getToken(context)
                    .map(AccessToken::getToken);
            accessToken = accessTokenMono.block();
            log.warn("Acquired local access token "+ accessToken);
        } catch (Exception e) {
            log.error("Failed to acquire local Access token getTokenFromLocalClientSecretCredentials() with cause: {}", 
                    e.getCause() != null ? e.getCause().toString() : e.getMessage(), e);
        }
        return accessToken;
    }

    /**
     * Gets a token from remote client secret credentials.
     * 
     * @return the access token, or null if acquisition fails
     */
    public String getTokenFromRemoteClientSecretCredentials() {
        String accessToken = null;
        
        try {
            final Configuration configuration = new Configuration();
            configuration.put(AZURE_CLIENT_ID, applicationParameters.getAzureDtsFiClientId());
            configuration.put(AZURE_TENANT_ID, applicationParameters.getAzureDtsFiTenantId());
            
            final ManagedIdentityCredential managedIdentityCredential = new ManagedIdentityCredentialBuilder()
                    .configuration(configuration)
                    .build();
            final TokenRequestContext context = getRemoteTokenRequestContext();
            final Mono<String> accessTokenMono = managedIdentityCredential.getToken(context)
                    .map(AccessToken::getToken);
            accessToken = accessTokenMono.block();
            log.debug("Acquired remote access token");
        } catch (Exception e) {
            log.error("Failed to acquire remote Access token getTokenFromRemoteClientSecretCredentials() with cause: {}", 
                    e.getCause() != null ? e.getCause().toString() : e.getMessage(), e);
        }
        return accessToken;
    }

    private TokenRequestContext getTokenRequestContext() {
        return new TokenRequestContext()
                .addScopes(applicationParameters.getAzureLocalScope());
    }

    private TokenRequestContext getRemoteTokenRequestContext() {
        return new TokenRequestContext()
                .addScopes(getRemoteScope());
    }

    private String getRemoteScope() {
        return "api://" + applicationParameters.getAzureDtsAppRegistrationId() + "/.default";
    }
}
