package uk.gov.hmcts.cp.services;

import uk.gov.hmcts.cp.domain.CourtListPublishStatusEntity;
import uk.gov.hmcts.cp.dto.CourtListPublishResponse;
import uk.gov.hmcts.cp.repositories.CourtListPublishStatusRepository;

import java.time.LocalDateTime;
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

    private final CourtListPublishStatusRepository repository;

    @Transactional
    public CourtListPublishResponse getByCourtListId(final UUID courtListId) {
        validateCourtListId(courtListId);
        LOG.atDebug().log("Fetching court list publish status for ID: {}", courtListId);
        
        CourtListPublishStatusEntity entity = repository.getByCourtListId(courtListId);
        if (entity == null) {
            LOG.atWarn().log("Court list publish status not found for ID: {}", courtListId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ERROR_ENTITY_NOT_FOUND);
        }
        return CourtListPublishResponse.from(entity);
    }

    @Transactional
    public CourtListPublishResponse createOrUpdate(
            final UUID courtListId,
            final UUID courtCentreId,
            final String publishStatus,
            final String courtListType) {
        validateCourtListId(courtListId);
        validateCourtCentreId(courtCentreId);
        validatePublishStatus(publishStatus);
        validateCourtListType(courtListType);

        LOG.atDebug().log("Creating or updating court list publish status for ID: {}", courtListId);
        CourtListPublishStatusEntity entity = repository.getByCourtListId(courtListId);

        if (entity != null) {
            LOG.atDebug().log("Updating existing court list publish status for ID: {}", courtListId);
            updateEntity(entity, courtCentreId, publishStatus, courtListType);
        } else {
            LOG.atDebug().log("Creating new court list publish status for ID: {}", courtListId);
            entity = createNewEntity(courtListId, courtCentreId, publishStatus, courtListType);
        }

        CourtListPublishStatusEntity savedEntity = repository.save(entity);
        return CourtListPublishResponse.from(savedEntity);
    }

    @Transactional
    public List<CourtListPublishResponse> findByCourtCentreId(final UUID courtCentreId) {
        validateCourtCentreId(courtCentreId);
        LOG.atDebug().log("Fetching court list publish statuses for court centre ID: {}", courtCentreId);
        return repository.findByCourtCentreId(courtCentreId).stream()
                .map(CourtListPublishResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<CourtListPublishResponse> findAll() {
        LOG.atDebug().log("Fetching all court list publish statuses");
        return repository.findAll().stream()
                .map(CourtListPublishResponse::from)
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

    private void validatePublishStatus(final String publishStatus) {
        if (publishStatus == null || publishStatus.isBlank()) {
            LOG.atWarn().log("No publish status provided");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ERROR_PUBLISH_STATUS_REQUIRED);
        }
    }

    private void validateCourtListType(final String courtListType) {
        if (courtListType == null || courtListType.isBlank()) {
            LOG.atWarn().log("No court list type provided");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ERROR_COURT_LIST_TYPE_REQUIRED);
        }
    }

    // Entity helper methods

    private void updateEntity(
            final CourtListPublishStatusEntity entity,
            final UUID courtCentreId,
            final String publishStatus,
            final String courtListType) {
        entity.setCourtCentreId(courtCentreId);
        entity.setPublishStatus(publishStatus);
        entity.setCourtListType(courtListType);
        entity.setLastUpdated(LocalDateTime.now());
    }

    private CourtListPublishStatusEntity createNewEntity(
            final UUID courtListId,
            final UUID courtCentreId,
            final String publishStatus,
            final String courtListType) {
        return new CourtListPublishStatusEntity(
                courtListId,
                courtCentreId,
                publishStatus,
                courtListType,
                LocalDateTime.now()
        );
    }
}

