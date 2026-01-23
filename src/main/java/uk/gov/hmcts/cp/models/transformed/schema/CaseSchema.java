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
public class CaseSchema {
    @JsonProperty("caseUrn")
    private String caseUrn;
    
    @JsonProperty("reportingRestriction")
    private Boolean reportingRestriction;
    
    @JsonProperty("caseSequenceIndicator")
    private String caseSequenceIndicator;
    
    @JsonProperty("party")
    private List<Party> party;
}
