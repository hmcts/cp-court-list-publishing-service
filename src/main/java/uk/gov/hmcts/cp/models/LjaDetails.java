package uk.gov.hmcts.cp.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Local Justice Area details from reference data (enforcement-area API),
 * aligned with progression.search.court.list LJA enrichment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LjaDetails {

    @JsonProperty("ljaCode")
    private String ljaCode;

    @JsonProperty("ljaName")
    private String ljaName;

    @JsonProperty("welshLjaName")
    private String welshLjaName;
}
