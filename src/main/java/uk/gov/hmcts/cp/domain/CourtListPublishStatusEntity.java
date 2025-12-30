package uk.gov.hmcts.cp.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "court_list_request_detail")
public class CourtListPublishStatusEntity {

    @Id
    @Column(name = "court_list_id", nullable = false)
    private UUID courtListId;

    @Column(name = "court_centre_id", nullable = false)
    private UUID courtCentreId;

    @Column(name = "publish_status", nullable = false)
    private String publishStatus;

    @Column(name = "court_list_type", nullable = false)
    private String courtListType;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @Column(name = "court_list_file_id")
    private UUID courtListFileId;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "error_message")
    private String errorMessage;

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

    public UUID getCourtListId() {
        return courtListId;
    }

    public UUID getCourtCentreId() {
        return courtCentreId;
    }

    public void setCourtCentreId(final UUID courtCentreId) {
        this.courtCentreId = courtCentreId;
    }

    public String getPublishStatus() {
        return publishStatus;
    }

    public void setPublishStatus(final String publishStatus) {
        this.publishStatus = publishStatus;
    }

    public String getCourtListType() {
        return courtListType;
    }

    public void setCourtListType(final String courtListType) {
        this.courtListType = courtListType;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(final LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public UUID getCourtListFileId() {
        return courtListFileId;
    }

    public void setCourtListFileId(final UUID courtListFileId) {
        this.courtListFileId = courtListFileId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(final String fileName) {
        this.fileName = fileName;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(final String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDate getPublishDate() {
        return publishDate;
    }

    public void setPublishDate(final LocalDate publishDate) {
        this.publishDate = publishDate;
    }
}

