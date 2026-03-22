package uk.gov.hmcts.cp.domain.sjp.cath;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

/**
 * CaTH case reference (mirrors staging PubHub schema Cases).
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SjpCathCases {

    String caseUrn;
}
