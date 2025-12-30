package uk.gov.hmcts.cp.services;

import static java.util.Objects.nonNull;

import uk.gov.hmcts.cp.domain.CourtListPublishStatusEntity;
import uk.gov.hmcts.cp.repositories.CourtListPublishStatusRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
    private final CourtListPublishStatusRepository repository;

    @Transactional
    public CourtListPublishStatusEntity getByCourtListId(final UUID courtListId) {
        if (!nonNull(courtListId)) {
            LOG.atWarn().log("No court list id provided");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Court list ID is required");
        }
        LOG.atDebug().log("Fetching court list publish status for ID: {}", courtListId);
        CourtListPublishStatusEntity entity = repository.getByCourtListId(courtListId);
        if (entity == null) {
            LOG.atWarn().log("Court list publish status not found for ID: {}", courtListId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Court list publish status not found");
        }
        return entity;
    }

    @Transactional
    public CourtListPublishStatusEntity createOrUpdate(
            final UUID courtListId,
            final UUID courtCentreId,
            final String publishStatus,
            final String courtListType) {
        if (!nonNull(courtListId)) {
            LOG.atWarn().log("No court list id provided");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Court list ID is required");
        }
        if (!nonNull(courtCentreId)) {
            LOG.atWarn().log("No court centre id provided");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Court centre ID is required");
        }
        if (publishStatus == null || publishStatus.isBlank()) {
            LOG.atWarn().log("No publish status provided");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Publish status is required");
        }
        if (courtListType == null || courtListType.isBlank()) {
            LOG.atWarn().log("No court list type provided");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Court list type is required");
        }

        LOG.atDebug().log("Creating or updating court list publish status for ID: {}", courtListId);
        CourtListPublishStatusEntity entity = repository.getByCourtListId(courtListId);

        if (entity != null) {
            LOG.atDebug().log("Updating existing court list publish status for ID: {}", courtListId);
            entity.setCourtCentreId(courtCentreId);
            entity.setPublishStatus(publishStatus);
            entity.setCourtListType(courtListType);
            entity.setLastUpdated(LocalDateTime.now());
        } else {
            LOG.atDebug().log("Creating new court list publish status for ID: {}", courtListId);
            entity = new CourtListPublishStatusEntity(
                    courtListId,
                    courtCentreId,
                    publishStatus,
                    courtListType,
                    LocalDateTime.now()
            );
        }

        return repository.save(entity);
    }

    @Transactional
    public CourtListPublishStatusEntity updateError(
            final UUID courtListId,
            final String errorMessage) {
        if (!nonNull(courtListId)) {
            LOG.atWarn().log("No court list id provided");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Court list ID is required");
        }

        LOG.atDebug().log("Updating error message for court list ID: {}", courtListId);
        CourtListPublishStatusEntity entity = repository.getByCourtListId(courtListId);
        if (entity == null) {
            LOG.atWarn().log("Court list publish status not found for ID: {}", courtListId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Court list publish status not found");
        }

        entity.setErrorMessage(errorMessage);
        entity.setLastUpdated(LocalDateTime.now());
        return repository.save(entity);
    }

    @Transactional
    public List<CourtListPublishStatusEntity> findByCourtCentreId(final UUID courtCentreId) {
        if (!nonNull(courtCentreId)) {
            LOG.atWarn().log("No court centre id provided");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Court centre ID is required");
        }
        LOG.atDebug().log("Fetching court list publish statuses for court centre ID: {}", courtCentreId);
        return repository.findByCourtCentreId(courtCentreId);
    }

    @Transactional
    public List<CourtListPublishStatusEntity> findByPublishStatus(final String publishStatus) {
        if (publishStatus == null || publishStatus.isBlank()) {
            LOG.atWarn().log("No publish status provided");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Publish status is required");
        }
        LOG.atDebug().log("Fetching court list publish statuses for status: {}", publishStatus);
        return repository.findByPublishStatus(publishStatus);
    }

    @Transactional
    public List<CourtListPublishStatusEntity> findByCourtCentreIdAndPublishStatus(
            final UUID courtCentreId,
            final String publishStatus) {
        if (!nonNull(courtCentreId)) {
            LOG.atWarn().log("No court centre id provided");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Court centre ID is required");
        }
        if (publishStatus == null || publishStatus.isBlank()) {
            LOG.atWarn().log("No publish status provided");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Publish status is required");
        }
        LOG.atDebug().log("Fetching court list publish statuses for court centre ID: {} and status: {}",
                courtCentreId, publishStatus);
        return repository.findByCourtCentreIdAndPublishStatus(courtCentreId, publishStatus);
    }

    @Transactional
    public void deleteByCourtListId(final UUID courtListId) {
        if (!nonNull(courtListId)) {
            LOG.atWarn().log("No court list id provided");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Court list ID is required");
        }
        LOG.atDebug().log("Deleting court list publish status for ID: {}", courtListId);
        CourtListPublishStatusEntity entity = repository.getByCourtListId(courtListId);
        if (entity == null) {
            LOG.atWarn().log("Court list publish status not found for ID: {}", courtListId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Court list publish status not found");
        }
        repository.deleteById(courtListId);
        LOG.atDebug().log("Successfully deleted court list publish status for ID: {}", courtListId);
    }

    @Transactional
    public List<CourtListPublishStatusEntity> findAll() {
        LOG.atDebug().log("Fetching all court list publish statuses");
        return repository.findAll();
    }
}

