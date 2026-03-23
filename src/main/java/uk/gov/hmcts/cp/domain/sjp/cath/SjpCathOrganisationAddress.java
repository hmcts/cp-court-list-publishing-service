package uk.gov.hmcts.cp.domain.sjp.cath;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * CaTH organisation address (mirrors staging PubHub schema OrganisationAddress).
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SjpCathOrganisationAddress {

    List<String> line;
    String town;
    String county;
    String postCode;
}
