package uk.gov.hmcts.cp.models.transformed.schema;

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
public class SessionSchema {
    @JsonProperty("judiciary")
    private List<Judiciary> judiciary;
    
    @JsonProperty("sittings")
    private List<Sitting> sittings;
}
