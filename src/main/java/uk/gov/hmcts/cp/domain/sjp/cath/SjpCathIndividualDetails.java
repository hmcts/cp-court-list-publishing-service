package uk.gov.hmcts.cp.domain.sjp.cath;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

/**
 * CaTH individual party details (mirrors staging PubHub schema IndividualDetails).
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SjpCathIndividualDetails {

    String title;
    String individualForenames;
    String individualSurname;
    String dateOfBirth;
    Integer age;
    SjpCathAddress address;
}
