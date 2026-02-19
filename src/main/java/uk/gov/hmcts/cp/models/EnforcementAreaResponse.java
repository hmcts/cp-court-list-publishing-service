package uk.gov.hmcts.cp.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from reference data enforcement-area API
 * (application/vnd.referencedata.query.enforcement-area+json).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnforcementAreaResponse {

    @JsonProperty("localJusticeArea")
    private LocalJusticeArea localJusticeArea;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LocalJusticeArea {
        @JsonProperty("nationalCourtCode")
        private String nationalCourtCode;
        @JsonProperty("name")
        private String name;
        @JsonProperty("welshName")
        private String welshName;
    }
}
