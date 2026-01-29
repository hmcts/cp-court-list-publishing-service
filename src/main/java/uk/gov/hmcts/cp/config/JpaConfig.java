package uk.gov.hmcts.cp.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import uk.gov.hmcts.cp.Application;

/**
 * JPA entity and repository scanning for non-test runs. When profile {@code test} is active,
 * {@link TestJpaConfig} is loaded instead (no JPA), so slice tests like @WebMvcTest can run
 * without EntityManagerFactory or a DataSource.
 */
@Configuration
@Profile("!test")
@EntityScan(basePackageClasses = Application.class)
@EnableJpaRepositories(basePackageClasses = Application.class)
public class JpaConfig {
}
