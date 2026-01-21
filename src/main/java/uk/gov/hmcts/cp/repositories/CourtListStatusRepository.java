package uk.gov.hmcts.cp.repositories;

import uk.gov.hmcts.cp.domain.CourtListStatusEntity;
import uk.gov.hmcts.cp.openapi.model.PublishStatus;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourtListStatusRepository extends JpaRepository<CourtListStatusEntity, UUID> {

    CourtListStatusEntity getByCourtListId(UUID courtListId);

    List<CourtListStatusEntity> findByCourtCentreId(UUID courtCentreId);

    List<CourtListStatusEntity> findByPublishStatus(PublishStatus publishStatus);

    List<CourtListStatusEntity> findByCourtCentreIdAndPublishStatus(UUID courtCentreId, PublishStatus publishStatus);

}

