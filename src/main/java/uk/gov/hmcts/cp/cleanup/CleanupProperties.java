package uk.gov.hmcts.cp.cleanup;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "cleanup")
public class CleanupProperties {

    private boolean enabled = true;
    private String cron = "0 0 2 * * ?";
    private int retentionDays = 90;
}
