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
public class Offence {
    @JsonProperty("code")
    private String code;
    
    @JsonProperty("title")
    private String title;
    
    @JsonProperty("cy_title")
    private String cyTitle;
    
    @JsonProperty("sum")
    private String sum;
    
    @JsonProperty("cy_sum")
    private String cySum;
}

