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

    /**
     * Numeric court id (e.g. "325") used for DtsMeta / CaTH publishing.
     */
    @JsonProperty("courtId")
    private String courtIdNumeric;
}
