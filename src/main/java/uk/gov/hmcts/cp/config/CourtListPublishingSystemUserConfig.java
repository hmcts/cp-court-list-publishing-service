package uk.gov.hmcts.cp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Holds the system user ID (UUID) used for reference data and document generator calls.
 * Set via environment variable or property: COURTLISTPUBLISHING_SYSTEM_USER_ID.
 * Optional at startup; if not set, the application starts but endpoints that need it will fail with a clear error.
 */
@Component
public class CourtListPublishingSystemUserConfig {

    private final String systemUserId;

    public CourtListPublishingSystemUserConfig(
            @Value("${COURTLISTPUBLISHING_SYSTEM_USER_ID:}") String systemUserId) {
        if (systemUserId != null && !systemUserId.isBlank()) {
            this.systemUserId = validateAndReturn(systemUserId);
        } else {
            this.systemUserId = null;
        }
    }

    private static String validateAndReturn(String value) {
        try {
            return UUID.fromString(value.trim()).toString();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "COURTLISTPUBLISHING_SYSTEM_USER_ID must be a valid UUID: " + value, e);
        }
    }

    /**
     * Returns the system user ID string (UUID format) for use in CJSCPPUID header, or null if not configured.
     */
    public String getSystemUserId() {
        return systemUserId;
    }
}
