package uk.gov.hmcts.cp.dto;

import uk.gov.hmcts.cp.domain.CourtListPublishStatusEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CourtListPublishResponse(
        @JsonProperty("courtListId")
        UUID courtListId,

        @JsonProperty("courtCentreId")
        UUID courtCentreId,

        @JsonProperty("publishStatus")
        String publishStatus,

        @JsonProperty("courtListType")
        String courtListType,

        @JsonProperty("lastUpdated")
        LocalDateTime lastUpdated,

        @JsonProperty("courtListFileId")
        UUID courtListFileId,

        @JsonProperty("fileName")
        String fileName,

        @JsonProperty("errorMessage")
        String errorMessage,

        @JsonProperty("publishDate")
        LocalDate publishDate
) {
    public static CourtListPublishResponse from(CourtListPublishStatusEntity entity) {
        return new CourtListPublishResponse(
                entity.getCourtListId(),
                entity.getCourtCentreId(),
                entity.getPublishStatus(),
                entity.getCourtListType(),
                entity.getLastUpdated(),
                entity.getCourtListFileId(),
                entity.getFileName(),
                entity.getErrorMessage(),
                entity.getPublishDate()
        );
    }
}

