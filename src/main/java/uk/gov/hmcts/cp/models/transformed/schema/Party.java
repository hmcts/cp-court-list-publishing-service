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
public class Party {
    @JsonProperty("partyRole")
    private String partyRole;
    
    @JsonProperty("individualDetails")
    private IndividualDetails individualDetails;
    
    @JsonProperty("offence")
    private List<OffenceSchema> offence;
    
    @JsonProperty("organisationDetails")
    private OrganisationDetails organisationDetails;
    
    @JsonProperty("subject")
    private Boolean subject;
}
