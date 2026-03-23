package uk.gov.hmcts.cp.domain.sjp.cath;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

/**
 * CaTH offence (mirrors staging PubHub schema Offence).
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SjpCathOffence {

    String offenceTitle;
    String offenceWording;
    Boolean reportingRestriction;
}
