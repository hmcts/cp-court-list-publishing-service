package uk.gov.hmcts.cp.domain.sjp.cath;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

/**
 * CaTH organisation party details (mirrors staging PubHub schema OrganisationDetails).
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SjpCathOrganisationDetails {

    String organisationName;
    SjpCathOrganisationAddress organisationAddress;
}
