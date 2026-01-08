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
public class Case {
    @JsonProperty("caseno")
    private String caseno;
    
    @JsonProperty("def_name")
    private String defName;
    
    @JsonProperty("def_dob")
    private String defDob;
    
    @JsonProperty("def_age")
    private Integer defAge;
    
    @JsonProperty("def_addr")
    private Address defAddr;
    
    @JsonProperty("inf")
    private String inf;
    
    @JsonProperty("offences")
    private Offences offences;
}

