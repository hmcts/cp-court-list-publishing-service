package uk.gov.hmcts.cp.cleanup;

import com.azure.storage.blob.BlobContainerClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.domain.CourtListStatusEntity;
import uk.gov.hmcts.cp.repositories.CourtListStatusRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class CleanupJobService {

    private static final String PDF_EXTENSION = ".pdf";

    private final CourtListStatusRepository repository;
    private final BlobContainerClient blobContainerClient;

    @Value("${cleanup.enabled:true}")
    private boolean cleanupEnabled;

    public CleanupJobService(CourtListStatusRepository repository,
                             @Autowired(required = false) BlobContainerClient blobContainerClient) {
        this.repository = repository;
        this.blobContainerClient = blobContainerClient;
    }

    public void cleanupOldData(int retentionDays) {
        cleanupOldData(retentionDays, null);
    }

    public void cleanupOldData(int retentionDays, String cronExpression) {
        if (!cleanupEnabled) {
            log.debug("Cleanup disabled, skipping");
            return;
        }
        if (blobContainerClient == null) {
            log.warn("Cleanup skipped: BlobContainerClient not available");
            return;
        }
        log.info("Cleanup started: retentionDays={}, cronExpression={}", retentionDays, cronExpression);
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        List<CourtListStatusEntity> entities = repository.findByLastUpdatedBefore(cutoff);
        log.info("Cleanup: found {} record(s) older than {} days (cutoff {})", entities.size(), retentionDays, cutoff);

        for (CourtListStatusEntity entity : entities) {
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
