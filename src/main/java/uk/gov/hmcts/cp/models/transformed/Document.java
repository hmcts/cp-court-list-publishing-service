package uk.gov.hmcts.cp.models.transformed;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {
    @JsonProperty("info")
    private DocumentInfo info;
    
    @JsonProperty("data")
    private DocumentData data;
}

