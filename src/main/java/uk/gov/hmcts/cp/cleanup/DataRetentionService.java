package uk.gov.hmcts.cp.cleanup;

import com.azure.storage.blob.BlobContainerClient;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import uk.gov.hmcts.cp.domain.CourtListStatusEntity;
import uk.gov.hmcts.cp.repositories.CourtListStatusRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@ConditionalOnProperty(name = "cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class DataRetentionService {

    private static final String PDF_EXTENSION = ".pdf";

    @Autowired
    private CourtListStatusRepository repository;

    @Autowired
    private RetentionPurgeProperties props;

    @Autowired
    private BlobContainerClient blobContainerClient;

    public void cleanupOldData() {

        Instant cutoff = LocalDate.now()
                .minusDays(props.getRetentionDays())
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();

        List<CourtListStatusEntity> oldRecords =
                repository.findByLastUpdatedBefore(cutoff);

        for (CourtListStatusEntity entity : oldRecords) {

            if (entity.getFileId() == null) {
                continue;
            }

            String blobName = entity.getFileId() + PDF_EXTENSION;
            try {
                boolean deleted = blobContainerClient.getBlobClient(blobName).deleteIfExists();
                if (deleted) {
                    repository.delete(entity);
                }
            } catch (Exception ex) {
                log.error("Failed to delete blob {}", blobName, ex);
            }
        }
    }
}
