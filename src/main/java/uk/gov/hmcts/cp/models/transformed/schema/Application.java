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
public class Application {
    @JsonProperty("party")
    private List<Party> party;
    
    @JsonProperty("applicationReference")
    private String applicationReference;
    
    @JsonProperty("reportingRestriction")
    private Boolean reportingRestriction;
    
    @JsonProperty("applicationType")
    private String applicationType;
}
