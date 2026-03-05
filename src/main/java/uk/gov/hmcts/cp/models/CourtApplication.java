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
public class CourtApplication {
    @JsonProperty("applicationType")
    private String applicationType;

    @JsonProperty("applicationParticulars")
    private String applicationParticulars;

    @JsonProperty("applicant")
    private CourtApplicationParty applicant;

    @JsonProperty("respondents")
    private List<CourtApplicationParty> respondents;

    @JsonProperty("subject")
    private CourtApplicationParty subject;
}
