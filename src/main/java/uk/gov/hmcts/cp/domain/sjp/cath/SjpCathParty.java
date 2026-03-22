package uk.gov.hmcts.cp.domain.sjp.cath;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

/**
 * CaTH party (defendant, prosecutor, etc.) (mirrors staging PubHub schema Party).
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SjpCathParty {

    String partyRole;
    SjpCathIndividualDetails individualDetails;
    SjpCathOrganisationDetails organisationDetails;
}
