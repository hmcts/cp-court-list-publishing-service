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
public class HearingSchema {
    @JsonProperty("hearingType")
    private String hearingType;
    
    @JsonProperty("case")
    private List<CaseSchema> caseList;
    
    @JsonProperty("panel")
    private String panel;
    
    @JsonProperty("channel")
    private List<String> channel;
    
    @JsonProperty("application")
    private List<Application> application;
}
