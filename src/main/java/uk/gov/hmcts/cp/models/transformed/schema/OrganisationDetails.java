package uk.gov.hmcts.cp.models.transformed.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganisationDetails {
    @JsonProperty("organisationName")
    private String organisationName;
    
    @JsonProperty("organisationAddress")
    private AddressSchema organisationAddress;
}
