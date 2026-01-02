package uk.gov.hmcts.cp.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Entity
@Table(name = "court_list_request_detail")
public class CourtListPublishStatusEntity {

    @Id
    @Column(name = "court_list_id", nullable = false)
    private UUID courtListId;

    @Setter
    @Column(name = "court_centre_id", nullable = false)
    private UUID courtCentreId;

    @Setter
    @Column(name = "publish_status", nullable = false)
    private String publishStatus;

    @Setter
    @Column(name = "court_list_type", nullable = false)
    private String courtListType;

    @Setter
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @Setter
    @Column(name = "court_list_file_id")
    private UUID courtListFileId;

    @Setter
    @Column(name = "file_name")
    private String fileName;

    @Setter
    @Column(name = "error_message")
    private String errorMessage;

    @Setter
    @Column(name = "publish_date")
    private LocalDate publishDate;

    protected CourtListPublishStatusEntity() {
    }

    public CourtListPublishStatusEntity(
            final UUID courtListId,
            final UUID courtCentreId,
            final String publishStatus,
            final String courtListType,
            final LocalDateTime lastUpdated) {
        this.courtListId = Objects.requireNonNull(courtListId);
        this.courtCentreId = Objects.requireNonNull(courtCentreId);
        this.publishStatus = Objects.requireNonNull(publishStatus);
        this.courtListType = Objects.requireNonNull(courtListType);
        this.lastUpdated = Objects.requireNonNull(lastUpdated);
    }

}

