package uk.gov.hmcts.cp.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class PublicCourtListConfig {

    @Bean
    @ConditionalOnProperty(name = "public-court-list.enabled", havingValue = "true")
    public RestTemplate publicCourtListRestTemplate() {
        return new RestTemplate();
    }
}
