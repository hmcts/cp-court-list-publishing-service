package uk.gov.hmcts.cp.cleanup;

import com.azure.storage.blob.BlobContainerClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.domain.CourtListStatusEntity;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.repositories.CourtListStatusRepository;
import uk.gov.hmcts.cp.services.CaTHService;
import uk.gov.hmcts.cp.services.CourtListPublisherBlobClientService;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class CleanupJobService {

    // Types whose publish flow stores only a CaTH JSON blob in Azure (no PDF).
    // Matches PROGRESSION_PDF_TYPES in CourtListPublishAndPDFGenerationTask.
    private static final Set<CourtListType> JSON_ONLY_TYPES = EnumSet.of(
            CourtListType.PUBLIC,
            CourtListType.STANDARD,
            CourtListType.BENCH
    );

    private final CourtListStatusRepository repository;
    private final BlobContainerClient blobContainerClient;

    public CleanupJobService(CourtListStatusRepository repository,
                             @Autowired(required = true) BlobContainerClient blobContainerClient) {
        this.repository = repository;
        this.blobContainerClient = blobContainerClient;
    }

    public void cleanupOldData(int retentionDays) {
        if (blobContainerClient == null) {
            log.warn("Cleanup skipped: BlobContainerClient not available");
            return;
        }
        log.info("Cleanup started: retentionDays={}", retentionDays);
        LocalDate cutoff = LocalDate.now().minusDays(retentionDays);
        List<CourtListStatusEntity> entities = repository.findByPublishDateBefore(cutoff);
        log.info("Cleanup: found {} record(s) older than {} days (cutoff {})", entities.size(), retentionDays, cutoff);

        for (CourtListStatusEntity entity : entities) {
            UUID fileId = entity.getFileId();
            boolean pdfOk;
            if (JSON_ONLY_TYPES.contains(entity.getCourtListType())) {
                // No PDF stored in Azure for these types; delete any orphaned PDF blob as best-effort
                if (fileId != null) {
                    deletePdfBlob(entity, fileId);
                }
                pdfOk = true;
            } else {
                // PDF+JSON types: PDF deletion must succeed before removing the DB record
                pdfOk = (fileId == null) || deletePdfBlob(entity, fileId);
            }
            boolean cathJsonOk = deleteCathPayloadBlob(entity);
            if (pdfOk && cathJsonOk) {
                repository.delete(entity);
                log.debug("Deleted record and blob(s) for court list {}", entity.getCourtListId());
            }
        }
    }

    /**
     * Delete PDF
     */
    private boolean deletePdfBlob(CourtListStatusEntity entity, UUID fileId) {
        String blobName = CourtListPublisherBlobClientService.buildPdfBlobName(fileId);
        try {
            return blobContainerClient.getBlobClient(blobName).deleteIfExists();
        } catch (Exception e) {
            log.warn("Failed to delete PDF blob {} for court list {}: {}", blobName, entity.getCourtListId(), e.getMessage());
            return false;
        }
    }

    /**
     * Delete CaTH JSON
     */
    private boolean deleteCathPayloadBlob(CourtListStatusEntity entity) {
        String blobName = CaTHService.buildBlobName(entity.getCourtListId());
        try {
            blobContainerClient.getBlobClient(blobName).deleteIfExists();
            return true;
        } catch (Exception e) {
            log.warn("Failed to delete CaTH JSON blob {} for court list {}: {}", blobName, entity.getCourtListId(), e.getMessage());
            return false;
        }
    }
}
