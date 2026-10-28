package uk.gov.hmcts.cp.services.crown;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.domain.CourtListStatusEntity;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishResponse;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.openapi.model.Status;
import uk.gov.hmcts.cp.repositories.CourtListStatusRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Accepts a request to publish a Crown court list to CaTH as one of the Crown schemas. Only the request is checked
 * here; the query, transformation, schema validation and CaTH send run later in {@code CrownPublishTask}.
 * <p>
 * The status is tracked in {@code court_list_publish_status} like other publishes, keyed by court centre, list type
 * and the start date. There is no file (no PDF), so the file status stays null, as it does for SJP lists.
 */
@Service
public class CrownCourtListPublishService {

    private static final Logger LOG = LoggerFactory.getLogger(CrownCourtListPublishService.class);

    private final CourtListStatusRepository repository;
    private final CrownTaskTriggerService taskTriggerService;

    public CrownCourtListPublishService(final CourtListStatusRepository repository,
                                       final CrownTaskTriggerService taskTriggerService) {
        this.repository = repository;
        this.taskTriggerService = taskTriggerService;
    }

    @Transactional
    public CourtListPublishResponse publish(final UUID courtCentreId, final CourtListType courtListType,
                                            final LocalDate startDate, final LocalDate endDate, final String userId) {
        validate(courtCentreId, courtListType, startDate, endDate, userId);

        final CourtListStatusEntity entity = findOrCreate(courtCentreId, courtListType, startDate);
        final CourtListStatusEntity saved = repository.save(entity);

        taskTriggerService.triggerCrownPublishTask(saved.getCourtListId(), courtCentreId, courtListType, startDate, endDate, userId);
        LOG.atInfo().log("Crown publish accepted for court list ID: {}, type: {} ({})",
                saved.getCourtListId(), courtListType, CrownListType.from(courtListType));
        return toResponse(saved);
    }

    private void validate(final UUID courtCentreId, final CourtListType courtListType,
                          final LocalDate startDate, final LocalDate endDate, final String userId) {
        if (courtCentreId == null) {
            throw badRequest("Court centre ID is required");
        }
        if (courtListType == null) {
            throw badRequest("Court list type is required");
        }
        if (!CrownListType.isSupported(courtListType)) {
            throw badRequest("Court list type " + courtListType + " cannot be published as a Crown list; use DRAFT or FINAL "
                    + "(daily list), WARN (advance list) or FIRM (firm list)");
        }
        if (startDate == null || endDate == null) {
            throw badRequest("Start date and end date are required");
        }
        if (endDate.isBefore(startDate)) {
            throw badRequest("End date must not be before start date");
        }
        if (userId == null || userId.isBlank()) {
            throw badRequest("CJSCPPUID header is required");
        }
    }

    /**
     * Reuses the row for the same court centre, list type and start date, putting it back to REQUESTED with the
     * previous attempt's error cleared; otherwise creates one.
     */
    private CourtListStatusEntity findOrCreate(final UUID courtCentreId, final CourtListType courtListType, final LocalDate publishDate) {
        final Optional<CourtListStatusEntity> existing = repository.findByCourtCentreIdAndPublishDateAndCourtListType(
                courtCentreId, publishDate, courtListType);
        if (existing.isPresent()) {
            final CourtListStatusEntity entity = existing.get();
            entity.setPublishStatus(Status.REQUESTED);
            entity.setFileStatus(Status.REQUESTED);
            entity.setPublishErrorMessage(null);
            entity.setLastUpdated(Instant.now());
            return entity;
        }
        final CourtListStatusEntity entity = new CourtListStatusEntity(
                UUID.randomUUID(), courtCentreId, Status.REQUESTED, null, courtListType, Instant.now());
        entity.setPublishDate(publishDate);
        // court_list_publish_status requires a file status on every non-SJP row (ck_file_status_required_for_non_sjp)
        entity.setFileStatus(Status.REQUESTED);
        return entity;
    }

    private static CourtListPublishResponse toResponse(final CourtListStatusEntity entity) {
        final OffsetDateTime lastUpdated = entity.getLastUpdated() != null
                ? entity.getLastUpdated().atOffset(ZoneOffset.UTC)
                : null;
        return new CourtListPublishResponse(
                entity.getCourtListId(),
                entity.getCourtCentreId(),
                entity.getPublishStatus(),
                entity.getFileStatus(),
                entity.getCourtListType(),
                lastUpdated,
                entity.getFileUrl(),
                entity.getFileId(),
                entity.getPublishErrorMessage(),
                entity.getFileErrorMessage(),
                entity.getPublishDate());
    }

    private static ResponseStatusException badRequest(final String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
