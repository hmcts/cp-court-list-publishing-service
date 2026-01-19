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

    @Value("${publishing-hub.url.v2:https://spnl-apim-int-gw.cpp.nonlive/publishing-hub/v2/publication}")
    private String publishingToCathUrl;

    @Value("${azure.local.mi.apimAuth.clientId:}")
    private String azureLocalMiApimAuthClientId;

    @Value("${azure.local.mi.tenantId:}")
    private String azureLocalMiTenantId;

    @Value("${azure.dts.fi.clientId:}")
    private String azureDtsFiClientId;

    @Value("${azure.dts.fi.tenantId:}")
    private String azureDtsFiTenantId;

    @Value("${azure.dts.appRegistration.id:}")
    private String azureDtsAppRegistrationId;

    @Value("${azure.local.scope:https://management.azure.com/.default}")
    private String azureLocalScope;
}
