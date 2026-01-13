package uk.gov.hmcts.cp.models.transformed;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Address {
    @JsonProperty("line1")
    private String line1;
    
    @JsonProperty("line2")
    private String line2;
    
    @JsonProperty("line3")
    private String line3;
    
    @JsonProperty("line4")
    private String line4;
    
    @JsonProperty("line5")
    private String line5;
    
    @JsonProperty("pcode")
    private String pcode;
}

