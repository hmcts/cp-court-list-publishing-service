package uk.gov.hmcts.cp.models;

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
public class Hearing {
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("sequence")
    private Integer sequence;
    
    @JsonProperty("reportingRestrictionReason")
    private String reportingRestrictionReason;
    
    @JsonProperty("welshReportingRestrictionReason")
    private String welshReportingRestrictionReason;
    
    @JsonProperty("startTime")
    private String startTime;
    
    @JsonProperty("hearingType")
    private String hearingType;
    
    @JsonProperty("welshHearingType")
    private String welshHearingType;
    
    @JsonProperty("caseNumber")
    private String caseNumber;
    
    @JsonProperty("caseId")
    private String caseId;
    
    @JsonProperty("prosecutorType")
    private String prosecutorType;
    
    @JsonProperty("hearingPublicListNote")
    private String hearingPublicListNote;
    
    @JsonProperty("defendants")
    private List<Defendant> defendants;
    
    @JsonProperty("adjournedHearingDate")
    private String adjournedHearingDate;
}

