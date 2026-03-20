package uk.gov.hmcts.cp.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourtRoom {
    @JsonProperty("courtRoomName")
    private String courtRoomName;
    
    @JsonProperty("welshCourtRoomName")
    private String welshCourtRoomName;
    
    @JsonProperty("judiciaryNames")
    private String judiciaryNames;
    
    @JsonProperty("welshJudiciaryNames")
    private String welshJudiciaryNames;
    
    @JsonProperty("timeslots")
    private List<Timeslot> timeslots;
}

