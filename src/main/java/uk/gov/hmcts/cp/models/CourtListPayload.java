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
public class CourtListPayload {
    @JsonProperty("listType")
    private String listType;
    
    @JsonProperty("courtCentreName")
    private String courtCentreName;
    
    @JsonProperty("courtCentreDefaultStartTime")
    private String courtCentreDefaultStartTime;
    
    @JsonProperty("courtCentreAddress1")
    private String courtCentreAddress1;
    
    @JsonProperty("courtCentreAddress2")
    private String courtCentreAddress2;

    /**
     * Venue address from reference data (when enriched). Used with address1-5 and postcode for schema line/postCode.
     */
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
    
    @JsonProperty("welshCourtCentreName")
    private String welshCourtCentreName;
    
    @JsonProperty("welshCourtCentreAddress1")
    private String welshCourtCentreAddress1;
    
    @JsonProperty("welshCourtCentreAddress2")
    private String welshCourtCentreAddress2;
    
    @JsonProperty("hearingDates")
    private List<HearingDate> hearingDates;
    
    @JsonProperty("templateName")
    private String templateName;

    @JsonProperty("ouCode")
    private String ouCode;

    @JsonProperty("courtId")
    private String courtId;

    /**
     * Numeric court id from reference data (e.g. "325"), used for CaTH DtsMeta.
     */
    @JsonProperty("courtIdNumeric")
    private String courtIdNumeric;

    /**
     * Whether the court centre supports Welsh, from reference data.
     */
    @JsonProperty("isWelsh")
    private Boolean isWelsh;
}

