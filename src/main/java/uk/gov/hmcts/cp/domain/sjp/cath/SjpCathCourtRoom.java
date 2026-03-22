package uk.gov.hmcts.cp.domain.sjp.cath;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * CaTH court room (mirrors staging PubHub schema CourtRoom).
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SjpCathCourtRoom {

    List<SjpCathSession> session;
}
