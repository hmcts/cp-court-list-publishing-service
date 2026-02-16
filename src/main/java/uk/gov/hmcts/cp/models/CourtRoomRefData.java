package uk.gov.hmcts.cp.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Court room data as returned inside reference data API response (courtroom object).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CourtRoomRefData {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("courtroomId")
    private Integer courtroomId;

    @JsonProperty("courtroomName")
    private String courtroomName;

    @JsonProperty("welshCourtroomName")
    private String welshCourtroomName;

    @JsonProperty("venueId")
    private Integer venueId;
}
