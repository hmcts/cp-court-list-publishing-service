package uk.gov.hmcts.cp.filters.jwt;

import jakarta.annotation.Resource;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Resource
@Builder
@Getter
@Setter
@ToString
public class AuthDetails {
    private String userName;
    /** User identifier (e.g. JWT subject) for system-user checks. */
    private String userId;
    private String scope;
}
