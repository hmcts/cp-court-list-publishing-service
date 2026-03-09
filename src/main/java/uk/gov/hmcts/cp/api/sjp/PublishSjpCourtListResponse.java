package uk.gov.hmcts.cp.api.sjp;

import lombok.Builder;
import lombok.Getter;

/**
 * Response after accepting or completing an SJP court list publish request.
 */
@Getter
@Builder
public class PublishSjpCourtListResponse {

    private final String status;
    private final String listType;
    private final String message;
}
