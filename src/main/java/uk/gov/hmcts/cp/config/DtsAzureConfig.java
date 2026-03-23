package uk.gov.hmcts.cp.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuration for DTS / Azure API Management and related identifiers used by publishing flows.
 * <p>
 * Values are bound from {@code application-azure.yml} and environment variables
 * (see project documentation for {@code AZURE_LOCAL_DTS_*} and {@code AZURE_REMOTE_DTS_*}).
 */
@Component
@Getter
public class DtsAzureConfig {

    @Value("${azure.local.dts.apimUrl:}") // gitleaks:allow
    private String azureLocalDtsApimUrl;

    @Value("${azure.local.dts.clientId:}") // gitleaks:allow
    private String azureLocalDtsClientId;

    @Value("${azure.local.dts.tenantId:}") // gitleaks:allow
    private String azureLocalDtsTenantId;

    @Value("${azure.local.dts.scope:}") // gitleaks:allow
    private String azureLocalDtsScope;

    @Value("${azure.remote.dts.clientId:}") // gitleaks:allow
    private String azureRemoteDtsClientId;

    @Value("${azure.remote.dts.tenantId:}") // gitleaks:allow
    private String azureRemoteDtsTenantId;

    @Value("${azure.remote.dts.appRegistration.id:}") // gitleaks:allow
    private String azureRemoteDtsAppRegistrationId;
}
