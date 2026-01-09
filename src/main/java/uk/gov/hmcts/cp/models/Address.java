package uk.gov.hmcts.cp.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Address {
    @JsonProperty("address1")
    private String address1;
    
    @JsonProperty("address2")
    private String address2;
    
    @JsonProperty("address3")
    private String address3;
    
    @JsonProperty("address4")
    private String address4;
    
    @JsonProperty("address5")
    private String address5;
    
    @JsonProperty("postcode")
    private String postcode;
}

