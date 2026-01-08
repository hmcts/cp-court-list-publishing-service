package uk.gov.hmcts.cp.models.transformed;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Session {
    @JsonProperty("lja")
    private String lja;
    
    @JsonProperty("court")
    private String court;
    
    @JsonProperty("room")
    private Integer room;
    
    @JsonProperty("sstart")
    private String sstart;
    
    @JsonProperty("blocks")
    private Blocks blocks;
}

