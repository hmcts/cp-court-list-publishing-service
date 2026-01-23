package uk.gov.hmcts.cp.models.transformed.schema;

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
public class CourtHouse {
    @JsonProperty("courtHouseName")
    private String courtHouseName;
    
    @JsonProperty("lja")
    private String lja;
    
    @JsonProperty("courtRoom")
    private List<CourtRoomSchema> courtRoom;
}
