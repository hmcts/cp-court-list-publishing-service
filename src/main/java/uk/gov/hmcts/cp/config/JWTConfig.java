package uk.gov.hmcts.cp.config;

import uk.gov.hmcts.cp.filters.jwt.AuthDetails;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.context.annotation.RequestScope;

@Configuration
public class JWTConfig {

    @Bean
    @RequestScope
    // attributes are set in the filter
    protected AuthDetails jwt() {
        return AuthDetails.builder().build();
    }

    @Bean
    public PathMatcher pathMatcher() {
        return new AntPathMatcher();
    }
}
