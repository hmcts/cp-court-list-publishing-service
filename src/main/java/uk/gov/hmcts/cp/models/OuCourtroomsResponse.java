package uk.gov.hmcts.cp.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Wrapper for reference data API response Accept: application/vnd.referencedata.ou-courtrooms+json.
 * GET /referencedata/courtrooms?courtId=... or ?courtCentreId=...
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OuCourtroomsResponse {

    @JsonProperty("organisationunits")
    private List<CourtCentreData> organisationunits;
}
