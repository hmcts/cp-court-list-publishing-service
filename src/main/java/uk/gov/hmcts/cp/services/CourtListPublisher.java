package uk.gov.hmcts.cp.services;

import uk.gov.hmcts.cp.domain.DtsMeta;

/**
 * Contract for publishing court list content to an external system (e.g. CaTH).
 * Allows a stub implementation for integration tests without test libraries.
 */
public interface CourtListPublisher {

    /**
     * Publish payload with metadata to the external system.
     *
     * @param payload  JSON or other serialized content
     * @param metadata publication metadata
     * @return HTTP status code (e.g. 200 for success)
     */
    int publish(String payload, DtsMeta metadata);
}
