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
public class AddressSchema {
    @JsonProperty("line")
    private List<String> line;
    
    @JsonProperty("town")
    private String town;
    
    @JsonProperty("county")
    private String county;
    
    @JsonProperty("postCode")
    private String postCode;
}
