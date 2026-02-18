package uk.gov.hmcts.cp.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Court centre data returned from reference data API
 * (referencedata.query.ou.courtrooms.ou-courtroom-name).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CourtCentreData {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("oucode")
    private String ouCode;

    @JsonProperty("oucodeL1Code")
    private String oucodeL1Code;

    @JsonProperty("oucodeL1Name")
    private String oucodeL1Name;

    @JsonProperty("oucodeL3Code")
    private String oucodeL3Code;

    @JsonProperty("oucodeL3Name")
    private String oucodeL3Name;

    /**
     * LJA (Local Justice Area) code from reference data, used to fetch LJA details (name, welsh name).
     */
    @JsonProperty("lja")
    private String lja;

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

    @JsonProperty("isWelsh")
    private Boolean isWelsh;

    @JsonProperty("oucodeL3WelshName")
    private String oucodeL3WelshName;

    @JsonProperty("welshAddress1")
    private String welshAddress1;

    @JsonProperty("welshAddress2")
    private String welshAddress2;

    @JsonProperty("welshAddress3")
    private String welshAddress3;

    @JsonProperty("welshAddress4")
    private String welshAddress4;

    @JsonProperty("welshAddress5")
    private String welshAddress5;

    @JsonProperty("defaultStartTime")
    private String defaultStartTime;

    @JsonProperty("defaultDurationHrs")
    private String defaultDurationHrs;

    @JsonProperty("courtLocationCode")
    private String courtLocationCode;

    /**
     * Numeric court id (e.g. "325") used for DtsMeta / CaTH publishing.
     */
    @JsonProperty("courtId")
    private String courtIdNumeric;

    @JsonProperty("courtroom")
    private CourtRoomRefData courtRoom;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("email")
    private String email;
}
