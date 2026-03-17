package uk.gov.hmcts.cp.cleanup;

import com.azure.storage.blob.BlobContainerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.repositories.CourtListStatusRepository;
import uk.gov.hmcts.cp.domain.CourtListStatusEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "cleanup.enabled", havingValue = "true", matchIfMissing = false)
@ConditionalOnBean(BlobContainerClient.class)
@RequiredArgsConstructor
@Slf4j
public class CleanupJobService {

    private static final String PDF_EXTENSION = ".pdf";

    private final CourtListStatusRepository repository;
    private final BlobContainerClient blobContainerClient;

    @Value("${cleanup.enabled:true}")
    private boolean cleanupEnabled;

    /**
     * Deletes court list publish status records (and their Azure blobs) older than the given
     * retention days. DB rows are only removed when the corresponding blob was successfully
     * deleted (or when there is no file id).
     *
     * @param retentionDays delete rows/blobs older than this many days
     */
    public void cleanupOldData(int retentionDays) {
        cleanupOldData(retentionDays, null);
    }

    /**
     * Same as {@link #cleanupOldData(int)} with optional cron expression for audit/logging.
     *
     * @param retentionDays delete rows/blobs older than this many days
     * @param cronExpression cron from the schedule-job request (for audit/logging), may be null
     */
    public void cleanupOldData(int retentionDays, String cronExpression) {
        if (!cleanupEnabled) {
            log.debug("Cleanup disabled, skipping");
            return;
        }
        log.info("Cleanup started: retentionDays={}, cronExpression={}", retentionDays, cronExpression);
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        List<CourtListStatusEntity> courtListStatusEntities = repository.findByLastUpdatedBefore(cutoff);
        log.info("Cleanup: found {} record(s) older than {} days (cutoff {})",
                courtListStatusEntities.size(), retentionDays, cutoff);

        for (CourtListStatusEntity entity : courtListStatusEntities) {
            boolean canDeleteRow = true;
            UUID fileId = entity.getFileId();
            if (fileId != null) {
                String blobName = fileId + PDF_EXTENSION;
                try {
                    canDeleteRow = blobContainerClient.getBlobClient(blobName).deleteIfExists();
                } catch (Exception e) {
                    log.warn("Failed to delete blob {} for court list {}: {}", blobName, entity.getCourtListId(), e.getMessage());
                    canDeleteRow = false;
                }
            }
            if (canDeleteRow) {
                repository.delete(entity);
                log.debug("Deleted record and blob for court list {}", entity.getCourtListId());
            }
        }
    }
}
