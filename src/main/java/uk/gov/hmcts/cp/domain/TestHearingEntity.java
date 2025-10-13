package uk.gov.hmcts.cp.domain;

import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "test_hearing")
public class TestHearingEntity {

    @Id
    @Column(name = "hearing_id", nullable = false)
    private UUID hearingId;

    @Column(name = "payload", nullable = false)
    private String payload;

    protected TestHearingEntity() {
    }

    public TestHearingEntity(final UUID hearingId, final String payload) {
        this.hearingId = Objects.requireNonNull(hearingId);
        this.payload = Objects.requireNonNull(payload);
    }

    public UUID getHearingId() {
        return hearingId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(final String payload) {
        this.payload = payload;
    }
}
