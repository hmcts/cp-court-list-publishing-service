package uk.gov.hmcts.cp.domain.sjp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * SJP list payload (PubHub parity). Contains generatedDateAndTime and readyCases
 * as supplied by SJP (e.g. public.sjp.pending-cases-public-list-generated).
 * Optional {@code courtIdNumeric} aligns with {@link uk.gov.hmcts.cp.models.CourtListPayload}
 * for CaTH {@code DtsMeta.courtId} (reference-data numeric id, e.g. {@code "325"}).
 * Optional {@code isWelsh} mirrors the court-centre flag used by the non-SJP publishing
 * flow: {@code true} → language "WELSH", otherwise "ENGLISH".
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SjpListPayload {

    private final String generatedDateAndTime;
    private final List<Map<String, Object>> readyCases;
    private final String courtIdNumeric;
    private final Boolean isWelsh;

    public SjpListPayload(String generatedDateAndTime, List<Map<String, Object>> readyCases) {
        this(generatedDateAndTime, readyCases, null, null);
    }

    public SjpListPayload(String generatedDateAndTime, List<Map<String, Object>> readyCases,
                          String courtIdNumeric) {
        this(generatedDateAndTime, readyCases, courtIdNumeric, null);
    }

    @JsonCreator
    public SjpListPayload(
            @JsonProperty("generatedDateAndTime") String generatedDateAndTime,
            @JsonProperty("readyCases") List<Map<String, Object>> readyCases,
            @JsonProperty("courtIdNumeric") String courtIdNumeric,
            @JsonProperty("isWelsh") Boolean isWelsh) {
        this.generatedDateAndTime = generatedDateAndTime;
        this.readyCases = readyCases;
        this.courtIdNumeric = courtIdNumeric;
        this.isWelsh = isWelsh;
    }
}
