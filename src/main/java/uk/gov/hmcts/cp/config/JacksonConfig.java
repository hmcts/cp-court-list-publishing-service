package uk.gov.hmcts.cp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    /**
     * Provides ObjectMapper bean required by task-manager-service's JsonObjectConverter.
     * Spring Boot's auto-configuration should provide this, but we ensure it's available
     * with proper configuration for Java 8 time types.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
