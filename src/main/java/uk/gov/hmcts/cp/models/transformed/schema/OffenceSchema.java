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
public class OffenceSchema {
    @JsonProperty("offenceCode")
    private String offenceCode;
    
    @JsonProperty("offenceTitle")
    private String offenceTitle;
    
    @JsonProperty("offenceWording")
    private String offenceWording;
    
    @JsonProperty("offenceMaxPen")
    private String offenceMaxPen;
    
    @JsonProperty("reportingRestriction")
    private Boolean reportingRestriction;

    @JsonProperty("reportingRestrictionDetails")
    private List<String> reportingRestrictionDetails;
    
    @JsonProperty("convictionDate")
    private String convictionDate;
    
    @JsonProperty("adjournedDate")
    private String adjournedDate;
    
    @JsonProperty("plea")
    private String plea;
    
    @JsonProperty("pleaDate")
    private String pleaDate;
    
    @JsonProperty("offenceLegislation")
    private String offenceLegislation;
}
