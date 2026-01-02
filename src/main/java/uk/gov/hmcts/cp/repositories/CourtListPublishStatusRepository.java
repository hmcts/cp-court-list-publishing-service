package uk.gov.hmcts.cp.repositories;

import uk.gov.hmcts.cp.domain.CourtListPublishStatusEntity;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourtListPublishStatusRepository extends JpaRepository<CourtListPublishStatusEntity, UUID> {

    CourtListPublishStatusEntity getByCourtListId(UUID courtListId);

    List<CourtListPublishStatusEntity> findByCourtCentreId(UUID courtCentreId);

    List<CourtListPublishStatusEntity> findByPublishStatus(String publishStatus);

    List<CourtListPublishStatusEntity> findByCourtCentreIdAndPublishStatus(UUID courtCentreId, String publishStatus);

}

