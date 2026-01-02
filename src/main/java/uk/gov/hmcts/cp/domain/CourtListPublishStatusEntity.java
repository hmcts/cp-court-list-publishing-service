package uk.gov.hmcts.cp.domain;

import static jakarta.persistence.EnumType.STRING;

import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.openapi.model.PublishStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Entity
@Table(name = "court_list_publish_status")
public class CourtListPublishStatusEntity {

    @Id
    @Column(name = "court_list_id", nullable = false)
    private UUID courtListId;

    @Setter
    @Column(name = "court_centre_id", nullable = false)
    private UUID courtCentreId;

    @Enumerated(STRING)
    @Setter
    @Column(name = "publish_status", nullable = false)
    private PublishStatus publishStatus;

    @Enumerated(STRING)
    @Setter
    @Column(name = "court_list_type", nullable = false)
    private CourtListType courtListType;

    @Setter
    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;

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
            final PublishStatus publishStatus,
            final CourtListType courtListType,
            final Instant lastUpdated) {
        this.courtListId = Objects.requireNonNull(courtListId);
        this.courtCentreId = Objects.requireNonNull(courtCentreId);
        this.publishStatus = publishStatus;
        this.courtListType = Objects.requireNonNull(courtListType);
        this.lastUpdated = Objects.requireNonNull(lastUpdated);
    }

}

