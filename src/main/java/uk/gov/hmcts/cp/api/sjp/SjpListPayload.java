package uk.gov.hmcts.cp.api.sjp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * SJP event list payload (same shape as PubHub: listPayload from public.sjp.* events).
 * Contains generatedDateAndTime and readyCases (each with defendant, prosecutor, sjpOffences, etc.).
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
