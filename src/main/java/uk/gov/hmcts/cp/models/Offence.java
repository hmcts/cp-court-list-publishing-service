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
public class Offence {
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("offenceTitle")
    private String offenceTitle;
    
    @JsonProperty("welshOffenceTitle")
    private String welshOffenceTitle;
    
    @JsonProperty("offenceWording")
    private String offenceWording;

    @JsonProperty("offenceCode")
    private String offenceCode;

    @JsonProperty("listingNumber")
    private Integer listingNumber;

    @JsonProperty("maxPenalty")
    private String maxPenalty;

    @JsonProperty("alcoholReadingAmount")
    private String alcoholReadingAmount;

    @JsonProperty("convictedOn")
    private String convictedOn;

    @JsonProperty("adjournedDate")
    private String adjournedDate;

    @JsonProperty("adjournedHearingType")
    private String adjournedHearingType;
}

