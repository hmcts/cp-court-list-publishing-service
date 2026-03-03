package uk.gov.hmcts.cp.openapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.UUID;

public class CourtListDownloadRequest {

    private UUID courtCentreId;
    private LocalDate startDate;
    private LocalDate endDate;
    private CourtListType courtListType;

    @JsonProperty("courtCentreId")
    public UUID getCourtCentreId() {
        return courtCentreId;
    }

    public void setCourtCentreId(UUID courtCentreId) {
        this.courtCentreId = courtCentreId;
    }

    @JsonProperty("startDate")
    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    @JsonProperty("endDate")
    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    @JsonProperty("courtListType")
    public CourtListType getCourtListType() {
        return courtListType;
    }

    public void setCourtListType(CourtListType courtListType) {
        this.courtListType = courtListType;
    }
}
