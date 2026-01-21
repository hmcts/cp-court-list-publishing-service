package uk.gov.hmcts.cp.services;

import uk.gov.hmcts.cp.domain.CourtListStatusEntity;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishResponse;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.openapi.model.PublishStatus;
import uk.gov.hmcts.cp.repositories.CourtListStatusRepository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CourtListPublishStatusService {

    private static final Logger LOG = LoggerFactory.getLogger(CourtListPublishStatusService.class);
    private static final String ERROR_COURT_LIST_ID_REQUIRED = "Court list ID is required";
    private static final String ERROR_COURT_CENTRE_ID_REQUIRED = "Court centre ID is required";
    private static final String ERROR_PUBLISH_STATUS_REQUIRED = "Publish status is required";
    private static final String ERROR_COURT_LIST_TYPE_REQUIRED = "Court list type is required";
    private static final String ERROR_ENTITY_NOT_FOUND = "Court list publish status not found";

    private final CourtListStatusRepository repository;

    @Transactional
    public CourtListPublishResponse getByCourtListId(final UUID courtListId) {
        validateCourtListId(courtListId);
        LOG.atDebug().log("Fetching court list publish status for ID: {}", courtListId);

        CourtListStatusEntity entity = repository.getByCourtListId(courtListId);
        if (entity == null) {
            LOG.atWarn().log("Court list publish status not found for ID: {}", courtListId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ERROR_ENTITY_NOT_FOUND);
        }
        return toResponse(entity);
    }

    @Transactional
    public CourtListPublishResponse createOrUpdate(
            final UUID courtListId,
            final UUID courtCentreId,
            final PublishStatus publishStatus,
            final CourtListType courtListType) {
        validateCourtListId(courtListId);
        validateCourtCentreId(courtCentreId);
        validatePublishStatus(publishStatus);
        validateCourtListType(courtListType);

        LOG.atDebug().log("Creating or updating court list publish status for ID: {}", courtListId);
        CourtListStatusEntity entity = repository.getByCourtListId(courtListId);

        if (entity != null) {
            LOG.atDebug().log("Updating existing court list publish status for ID: {}", courtListId);
            updateEntity(entity, courtCentreId, publishStatus, courtListType);
        } else {
            LOG.atDebug().log("Creating new court list publish status for ID: {}", courtListId);
            entity = createNewEntity(courtListId, courtCentreId, publishStatus, courtListType);
        }

        CourtListStatusEntity savedEntity = repository.save(entity);
        return toResponse(savedEntity);
    }

    @Transactional
    public List<CourtListPublishResponse> findByCourtCentreId(final UUID courtCentreId) {
        validateCourtCentreId(courtCentreId);
        LOG.atDebug().log("Fetching court list publish statuses for court centre ID: {} (limited to 10 records)", courtCentreId);
        return repository.findByCourtCentreId(courtCentreId).stream()
                .limit(10)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<CourtListPublishResponse> findAll() {
        LOG.atDebug().log("Fetching all court list publish statuses");
        return repository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // Validation helper methods

    private void validateCourtListId(final UUID courtListId) {
        if (Objects.isNull(courtListId)) {
            LOG.atWarn().log("No court list id provided");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ERROR_COURT_LIST_ID_REQUIRED);
        }
    }

    private void validateCourtCentreId(final UUID courtCentreId) {
        if (Objects.isNull(courtCentreId)) {
            LOG.atWarn().log("No court centre id provided");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ERROR_COURT_CENTRE_ID_REQUIRED);
        }
    }

    private void validatePublishStatus(final PublishStatus publishStatus) {
        if (publishStatus == null) {
            LOG.atWarn().log("No publish status provided");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ERROR_PUBLISH_STATUS_REQUIRED);
        }
    }

    private void validateCourtListType(final CourtListType courtListType) {
        if (courtListType == null) {
            LOG.atWarn().log("No court list type provided");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ERROR_COURT_LIST_TYPE_REQUIRED);
        }
    }

    // Entity helper methods

    private void updateEntity(
            final CourtListStatusEntity entity,
            final UUID courtCentreId,
            final PublishStatus publishStatus,
            final CourtListType courtListType) {
        entity.setCourtCentreId(courtCentreId);
        entity.setPublishStatus(publishStatus);
        entity.setCourtListType(courtListType);
        entity.setLastUpdated(Instant.now());
    }

    private CourtListStatusEntity createNewEntity(
            final UUID courtListId,
            final UUID courtCentreId,
            final PublishStatus publishStatus,
            final CourtListType courtListType) {
        return new CourtListStatusEntity(
                courtListId,
                courtCentreId,
                publishStatus,
                courtListType,
                Instant.now()
        );
    }

    // Mapper method to convert entity to response
    private CourtListPublishResponse toResponse(final CourtListStatusEntity entity) {
        OffsetDateTime lastUpdated = entity.getLastUpdated() != null
                ? entity.getLastUpdated().atOffset(ZoneOffset.UTC)
                : null;

        if (entity.getCourtListType() == null) {
            LOG.atError().log("Court list type is null for entity with courtListId: {}", entity.getCourtListId());
            throw new IllegalStateException("Court list type is required and cannot be null");
        }

        // Convert String publishStatus to PublishStatus enum
        PublishStatus publishStatusEnum = entity.getPublishStatus();
        
        return new CourtListPublishResponse(
                entity.getCourtListId(),
                entity.getCourtCentreId(),
                publishStatusEnum,
                entity.getCourtListType(),
                lastUpdated,
                entity.getCourtListFileId(),
                entity.getFileName(),
                entity.getErrorMessage(),
                entity.getPublishDate()
        );
    }
}

