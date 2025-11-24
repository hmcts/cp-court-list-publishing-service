package uk.gov.hmcts.cp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuditFilterConfig {

    /**
     * Provides the openapi-rest-spec String bean required by cp-audit-filter-springboot.
     * This bean is required even when audit.http.enabled=false because the library
     * creates the OpenApiSpecificationParser bean unconditionally.
     */
    @Bean
    public String auditOpenApiRestSpec(@Value("${audit.http.openapi-rest-spec:court-list-publishing-api.openapi.yml}") String openApiRestSpec) {
        return openApiRestSpec;
    }

    /**
     * Provides the enabled boolean bean required by cp-audit-filter-springboot.
     * This bean is required even when audit.http.enabled=false because the library
     * creates the OpenApiSpecificationParser bean unconditionally.
     */
    @Bean
    public Boolean auditHttpEnabled(@Value("${audit.http.enabled:false}") boolean enabled) {
        return enabled;
    }
}

