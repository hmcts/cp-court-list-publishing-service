package uk.gov.hmcts.cp.repositories;

import uk.gov.hmcts.cp.domain.TestHearingEntity;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HearingRepository extends JpaRepository<TestHearingEntity, UUID> {

    TestHearingEntity getByHearingId(UUID hearingId);

}
