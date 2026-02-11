package uk.gov.hmcts.cp.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.domain.DtsMeta;

/**
 * No-op publisher used when profile {@code integration} is active and no HTTP-based publisher is used.
 * {@link IntegrationCourtListPublisher} is @Primary in integration and calls WireMock instead.
 */
@Component
@Profile("integration")
@Slf4j
public class StubCourtListPublisher implements CourtListPublisher {

    @Override
    public int publish(String payload, DtsMeta metadata) {
        log.debug("StubCourtListPublisher: no-op publish (integration profile)");
        return HttpStatus.OK.value();
    }
}
