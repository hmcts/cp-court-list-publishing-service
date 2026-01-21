package uk.gov.hmcts.cp.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Application parameters configuration.
 * 
 * <p>This class holds configuration values for the application,
 * including Publishing Hub URLs and Azure configuration.
 */
@Component
@Getter
public class ApplicationParameters {

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
