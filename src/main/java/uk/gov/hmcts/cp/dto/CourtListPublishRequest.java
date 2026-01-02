package uk.gov.hmcts.cp.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CourtListPublishRequest(
        @JsonProperty("courtListId")
        UUID courtListId,

        @JsonProperty("courtCentreId")
        UUID courtCentreId,

        @JsonProperty("publishStatus")
        String publishStatus,

        @JsonProperty("courtListType")
        String courtListType
) {
    public CourtListPublishRequest {
        if (courtListId == null) {
            throw new IllegalArgumentException("Court list ID cannot be null");
        }
        if (courtCentreId == null) {
            throw new IllegalArgumentException("Court centre ID cannot be null");
        }
        if (publishStatus == null || publishStatus.isBlank()) {
            throw new IllegalArgumentException("Publish status cannot be null or empty");
        }
        if (courtListType == null || courtListType.isBlank()) {
            throw new IllegalArgumentException("Court list type cannot be null or empty");
        }
    }
}

