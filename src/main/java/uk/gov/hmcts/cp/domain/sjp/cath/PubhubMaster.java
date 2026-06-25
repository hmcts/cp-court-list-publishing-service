package uk.gov.hmcts.cp.domain.sjp.cath;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * CaTH root payload (mirrors staging PubHub schema PubhubMaster).
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PubhubMaster {

    SjpCathDocument document;
    List<SjpCathCourtLists> courtLists;
}
