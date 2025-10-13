package uk.gov.hmcts.cp.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HearingRequest(
        @JsonProperty("hearingId")
        UUID hearingId,

        @JsonProperty("payload")
        String payload
) {
    public HearingRequest {
        if (hearingId == null) {
            throw new IllegalArgumentException("HearingId cannot be null");
        }
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Payload cannot be null or empty");
        }
    }
}
