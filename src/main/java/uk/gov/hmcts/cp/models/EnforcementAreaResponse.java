package uk.gov.hmcts.cp.models;

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
public class EnforcementAreaResponse {

    @JsonProperty("localJusticeArea")
    private LocalJusticeArea localJusticeArea;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocalJusticeArea {
        @JsonProperty("nationalCourtCode")
        private String nationalCourtCode;
        @JsonProperty("name")
        private String name;
        @JsonProperty("welshName")
        private String welshName;
    }
}
