package uk.gov.hmcts.cp.models.transformed;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.cp.models.transformed.schema.CourtList;
import uk.gov.hmcts.cp.models.transformed.schema.DocumentSchema;
import uk.gov.hmcts.cp.models.transformed.schema.Venue;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourtListDocument {
    @JsonProperty("document")
    private DocumentSchema document;
    
    @JsonProperty("venue")
    private Venue venue;
    
    @JsonProperty("courtLists")
    private List<CourtList> courtLists;
}

