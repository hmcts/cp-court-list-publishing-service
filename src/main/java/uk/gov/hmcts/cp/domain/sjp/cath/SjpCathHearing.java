package uk.gov.hmcts.cp.domain.sjp.cath;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * CaTH hearing (one per ready case) (mirrors staging PubHub schema Hearing).
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SjpCathHearing {

    List<SjpCathParty> party;
    List<SjpCathOffence> offence;
    List<SjpCathCases> cases;
}
