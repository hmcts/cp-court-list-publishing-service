package uk.gov.hmcts.cp.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Application parameters configuration.
 * 
 * <p>This class holds configuration values for the application,
 * including Publishing Hub URLs and Azure configuration.
 * 
 * <p>Configuration values can be set via environment variables:
 * <ul>
 *   <li>azure.local.dts.apimUrl → AZURE_LOCAL_DTS_APIMURL or AZURE_LOCAL_DTS_APIM_URL</li>
 *   <li>azure.local.dts.clientId → AZURE_LOCAL_DTS_CLIENTID or AZURE_LOCAL_DTS_CLIENT_ID</li>
 *   <li>azure.local.dts.tenantId → AZURE_LOCAL_DTS_TENANTID or AZURE_LOCAL_DTS_TENANT_ID</li>
 *   <li>azure.local.dts.scope → AZURE_LOCAL_DTS_SCOPE</li>
 *   <li>azure.remote.dts.clientId → AZURE_REMOTE_DTS_CLIENTID or AZURE_REMOTE_DTS_CLIENT_ID</li>
 *   <li>azure.remote.dts.tenantId → AZURE_REMOTE_DTS_TENANTID or AZURE_REMOTE_DTS_TENANT_ID</li>
 *   <li>azure.remote.dts.appRegistration.id → AZURE_REMOTE_DTS_APPREGISTRATION_ID or AZURE_REMOTE_DTS_APP_REGISTRATION_ID</li>
 * </ul>
 * 
 * <p>Spring Boot automatically converts property names to environment variables:
 * <ul>
 *   <li>Dots (.) become underscores (_)</li>
 *   <li>Case is ignored (uppercase/lowercase both work)</li>
 *   <li>CamelCase can be converted with or without underscores</li>
 * </ul>
 */
@Component
@Getter
public class DtsAzureConfig {

    @Value("${azure.local.dts.apimUrl:https://spnl-apim-int-gw.cpp.nonlive/publishing-hub/v2/publication}")
    private String azureLocalDtsApimUrl;

    @Value("${azure.local.dts.clientId:}")
    private String azureLocalDtsClientId;

    @Value("${azure.local.dts.tenantId:}")
    private String azureLocalDtsTenantId;

    @Value("${azure.local.dts.scope:https://management.azure.com/.default}")
    private String azureLocalDtsScope;

    @Value("${azure.remote.dts.clientId:}")
    private String azureRemoteDtsClientId;

    @Value("${azure.remote.dts.tenantId:}")
    private String azureRemoteDtsTenantId;

    @Value("${azure.remote.dts.appRegistration.id:}")
    private String azureRemoteDtsAppRegistrationId;
}
