package uk.gov.hmcts.cp.services.crown;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.cp.domain.CourtListStatusEntity;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishResponse;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.openapi.model.Status;
import uk.gov.hmcts.cp.repositories.CourtListStatusRepository;
import uk.gov.hmcts.cp.services.CourtListPublishStatusService;
import uk.gov.hmcts.cp.services.CourtListStatusUpdater;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Status rows for crown list publishing, in {@code court_list_publish_status}. A crown publish produces no file,
 * so (as for SJP) only the publish status is set: a new row has no file status.
 *
 * <p>One row stands for one list: court centre, list type and start date. One publish of a list runs at a time:
 * the row is locked while it is checked, and a request while a publish is still {@code REQUESTED} is refused.
 * A publish {@code REQUESTED} for longer than the in-progress timeout ({@code crown.publish.in-progress-timeout},
 * default 15 minutes) is treated as abandoned, so it cannot block the list for good.
 */
@Service
public class CrownListStatusService {

    static final String UNIQUE_INDEX = "ux_court_list_publish_status_non_sjp";

    private final CourtListStatusRepository repository;
    private final CourtListStatusUpdater statusUpdater;
    private final CourtListPublishStatusService courtListPublishStatusService;
    private final Duration inProgressTimeout;

    public CrownListStatusService(CourtListStatusRepository repository,
                                  CourtListStatusUpdater statusUpdater,
                                  CourtListPublishStatusService courtListPublishStatusService,
                                  @Value("${crown.publish.in-progress-timeout:PT15M}") Duration inProgressTimeout) {
        this.repository = repository;
        this.statusUpdater = statusUpdater;
        this.courtListPublishStatusService = courtListPublishStatusService;
        this.inProgressTimeout = inProgressTimeout;
    }

    /**
     * Finds or creates the row for the list and sets its publish status to REQUESTED.
     *
     * @throws CrownPublishInProgressException when a publish of the same list is still in progress
     */
    @Transactional
    public CourtListPublishResponse requestPublish(UUID courtCentreId, CourtListType courtListType, LocalDate startDate) {
        UUID courtListId = repository.lockByCourtCentreIdAndPublishDateAndCourtListType(courtCentreId, startDate, courtListType)
                .map(existing -> {
                    if (isInProgress(existing)) {
                        throw inProgress(courtListType, startDate, null);
                    }
                    statusUpdater.markPublishRequested(existing.getCourtListId());
                    return existing.getCourtListId();
                })
                .orElseGet(() -> create(courtCentreId, courtListType, startDate));
        return courtListPublishStatusService.findPublishStatus(courtListId, null, null, null).get(0);
    }

    private UUID create(UUID courtCentreId, CourtListType courtListType, LocalDate startDate) {
        CourtListStatusEntity row = new CourtListStatusEntity(
                UUID.randomUUID(), courtCentreId, Status.REQUESTED, null, courtListType, Instant.now());
        row.setPublishDate(startDate);
        try {
            // flushed now, so a concurrent request that created the same row first is caught here
            repository.saveAndFlush(row);
        } catch (DataIntegrityViolationException e) {
            if (isUniqueIndexViolation(e)) {
                throw inProgress(courtListType, startDate, e);
            }
            throw e;
        }
        return row.getCourtListId();
    }

    private boolean isInProgress(CourtListStatusEntity row) {
        return row.getPublishStatus() == Status.REQUESTED
                && row.getLastUpdated() != null
                && row.getLastUpdated().isAfter(Instant.now().minus(inProgressTimeout));
    }

    private static boolean isUniqueIndexViolation(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(UNIQUE_INDEX)) {
                return true;
            }
        }
        return false;
    }

    private static CrownPublishInProgressException inProgress(CourtListType type, LocalDate startDate, Throwable cause) {
        return new CrownPublishInProgressException("A " + type + " list publish starting " + startDate
                + " for this court centre is already in progress", cause);
    }
}
