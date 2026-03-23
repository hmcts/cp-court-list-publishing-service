package uk.gov.hmcts.cp.domain.sjp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * SJP list payload (PubHub parity). Contains generatedDateAndTime and readyCases
 * as supplied by SJP (e.g. public.sjp.pending-cases-public-list-generated).
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SjpListPayload {

    private final String generatedDateAndTime;
    private final List<Map<String, Object>> readyCases;

    public SjpListPayload(
            @JsonProperty("generatedDateAndTime") String generatedDateAndTime,
            @JsonProperty("readyCases") List<Map<String, Object>> readyCases) {
        this.generatedDateAndTime = generatedDateAndTime;
        this.readyCases = readyCases;
    }
}
