package uk.gov.hmcts.cp.services;

import uk.gov.hmcts.cp.domain.CourtListStatusEntity;
import uk.gov.hmcts.cp.openapi.model.Status;
import uk.gov.hmcts.cp.repositories.CourtListStatusRepository;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Shared publish/file status bookkeeping for {@code court_list_publish_status}, used by both
 * {@code CourtListPublishAndPDFGenerationTask} (publish + file) and {@code SjpPublishTask}
 * (publish only). A success clears the corresponding error message; a failure records the
 * full stack trace.
 *
 * <p>Every attempt reopens the cycle by marking REQUESTED — the accept step for both flows, plus
 * {@code SjpPublishTask} for its own attempt — so a row never sticks on a previous attempt's
 * outcome. {@link #shouldRecord} is the single rule both status pairs go through, so that
 * attempts finishing out of order cannot bury a recorded success.
 */
@Component
@RequiredArgsConstructor
public class CourtListStatusUpdater {

    private static final Logger LOGGER = LoggerFactory.getLogger(CourtListStatusUpdater.class);

    private final CourtListStatusRepository repository;

    /**
     * Starts a fresh publish attempt: back to REQUESTED, with any previous attempt's error
     * cleared so a stale stack trace cannot outlive the failure it describes.
     */
    public void markPublishRequested(UUID courtListId) {
        updatePublishStatus(courtListId, Status.REQUESTED,
                entity -> entity.setPublishErrorMessage(null));
    }

    public void markPublishSuccessful(UUID courtListId) {
        updatePublishStatus(courtListId, Status.SUCCESSFUL,
                entity -> entity.setPublishErrorMessage(null));
    }

    public void markPublishFailed(UUID courtListId, Exception e) {
        updatePublishStatus(courtListId, Status.FAILED,
                entity -> entity.setPublishErrorMessage(buildErrorMessage(e)));
    }

    public void markFileSuccessful(UUID courtListId, UUID fileId) {
        updateFileStatus(courtListId, Status.SUCCESSFUL, entity -> {
            entity.setFileId(fileId);
            entity.setFileErrorMessage(null);
            entity.setPublishCount(entity.getPublishCount() + 1);
        });
    }

    public void markFileFailed(UUID courtListId, Exception e) {
        updateFileStatus(courtListId, Status.FAILED,
                entity -> entity.setFileErrorMessage(buildErrorMessage(e)));
    }

    private void updatePublishStatus(UUID courtListId, Status statusToRecord,
                                     Consumer<CourtListStatusEntity> details) {
        withEntity(courtListId, entity -> {
            if (!shouldRecord(entity.getPublishStatus(), statusToRecord)) {
                logIgnored("publish", courtListId, entity.getPublishStatus(), statusToRecord);
                return false;
            }
            entity.setPublishStatus(statusToRecord);
            details.accept(entity);
            return true;
        });
    }

    private void updateFileStatus(UUID courtListId, Status statusToRecord,
                                  Consumer<CourtListStatusEntity> details) {
        withEntity(courtListId, entity -> {
            if (!shouldRecord(entity.getFileStatus(), statusToRecord)) {
                logIgnored("file", courtListId, entity.getFileStatus(), statusToRecord);
                return false;
            }
            entity.setFileStatus(statusToRecord);
            details.accept(entity);
            return true;
        });
    }

    private static void logIgnored(String statusName, UUID courtListId,
                                   Status statusOnRow, Status statusToRecord) {
        LOGGER.warn("Ignoring {} status {} for court list ID {}: a later attempt has already recorded {}, "
                + "which stands until the next attempt", statusName, statusToRecord, courtListId, statusOnRow);
    }

    /**
     * Whether this attempt's outcome should be written to the row. Only one combination is ever
     * refused: recording FAILED over a row that already reads SUCCESSFUL — the list did reach
     * CaTH (or the file was uploaded), so an attempt finishing late must not bury that.
     * Everything else is written, including the REQUESTED each new attempt starts with and a
     * repeated failure refreshing its own error.
     *
     * <p>It is deliberately not gated on the row still reading REQUESTED. Two attempts can be in
     * flight at once and each marks REQUESTED at its own start, so a fast failure can land
     * between a slower attempt's REQUESTED and its success — requiring a REQUESTED between the
     * two outcomes would drop that success and leave the row FAILED for a live list.
     *
     * @param statusOnRow    the status currently stored on the row (this or another attempt wrote
     *                       it; null where nothing has been recorded yet)
     * @param statusToRecord the status this attempt is trying to write
     */
    private static boolean shouldRecord(Status statusOnRow, Status statusToRecord) {
        boolean recordingAFailure = Status.FAILED == statusToRecord;
        boolean rowAlreadySucceeded = Status.SUCCESSFUL == statusOnRow;

        if (recordingAFailure && rowAlreadySucceeded) {
            return false;
        }
        return true;
    }

    /**
     * Applies {@code mutator} to the row and saves it. A mutator returning {@code false} declined
     * the update, leaving the row — including its {@code lastUpdated} — exactly as it was.
     */
    private void withEntity(UUID courtListId, Predicate<CourtListStatusEntity> mutator) {
        CourtListStatusEntity entity = repository.getByCourtListId(courtListId);
        if (entity == null) {
            LOGGER.warn("No court list publish status record found for court list ID: {}", courtListId);
            return;
        }
        if (!mutator.test(entity)) {
            return;
        }
        entity.setLastUpdated(Instant.now());
        repository.save(entity);
    }

    private static String buildErrorMessage(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
