package uk.gov.hmcts.cp.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import uk.gov.hmcts.cp.domain.TestHearingEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class HearingRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private HearingRepository hearingRepository;

    @AfterEach
    void clearData() {
        hearingRepository.deleteAll();
        entityManager.clear();
    }

    @Test
    void save_shouldPersistHearing_whenValidEntityProvided() {
        // Given
        UUID hearingId = UUID.randomUUID();
        String payload = "{\"caseId\":\"12345\",\"hearingType\":\"FINAL\"}";
        TestHearingEntity hearing = new TestHearingEntity(hearingId, payload);

        // When
        TestHearingEntity savedHearing = hearingRepository.save(hearing);
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(savedHearing).isNotNull();
        assertThat(savedHearing.getHearingId()).isEqualTo(hearingId);
        assertThat(savedHearing.getPayload()).isEqualTo(payload);
    }

    @Test
    void findById_shouldReturnHearing_whenHearingExists() {
        // Given
        UUID hearingId = UUID.randomUUID();
        String payload = "{\"caseId\":\"12345\",\"hearingType\":\"FINAL\"}";
        TestHearingEntity hearing = new TestHearingEntity(hearingId, payload);
        entityManager.persistAndFlush(hearing);
        entityManager.clear();

        // When
        Optional<TestHearingEntity> foundHearing = hearingRepository.findById(hearingId);

        // Then
        assertThat(foundHearing).isPresent();
        assertThat(foundHearing.get().getHearingId()).isEqualTo(hearingId);
        assertThat(foundHearing.get().getPayload()).isEqualTo(payload);
    }

    @Test
    void findById_shouldReturnEmpty_whenHearingDoesNotExist() {
        // Given
        UUID nonExistentHearingId = UUID.randomUUID();

        // When
        Optional<TestHearingEntity> foundHearing = hearingRepository.findById(nonExistentHearingId);

        // Then
        assertThat(foundHearing).isEmpty();
    }

    @Test
    void getByHearingId_shouldReturnHearing_whenHearingExists() {
        // Given
        UUID hearingId = UUID.randomUUID();
        String payload = "{\"caseId\":\"12345\",\"hearingType\":\"FINAL\"}";
        TestHearingEntity hearing = new TestHearingEntity(hearingId, payload);
        entityManager.persistAndFlush(hearing);
        entityManager.clear();

        // When
        TestHearingEntity foundHearing = hearingRepository.getByHearingId(hearingId);

        // Then
        assertThat(foundHearing).isNotNull();
        assertThat(foundHearing.getHearingId()).isEqualTo(hearingId);
        assertThat(foundHearing.getPayload()).isEqualTo(payload);
    }


    @Test
    void update_shouldModifyPayload_whenHearingExists() {
        // Given
        UUID hearingId = UUID.randomUUID();
        String originalPayload = "{\"caseId\":\"12345\",\"hearingType\":\"FINAL\"}";
        TestHearingEntity hearing = new TestHearingEntity(hearingId, originalPayload);
        entityManager.persistAndFlush(hearing);
        entityManager.clear();

        // When
        TestHearingEntity existingHearing = hearingRepository.findById(hearingId).orElseThrow();
        String newPayload = "{\"caseId\":\"12345\",\"hearingType\":\"PRELIMINARY\"}";
        existingHearing.setPayload(newPayload);
        TestHearingEntity updatedHearing = hearingRepository.save(existingHearing);
        entityManager.flush();
        entityManager.clear();

        // Then
        TestHearingEntity retrievedHearing = hearingRepository.findById(hearingId).orElseThrow();
        assertThat(retrievedHearing.getPayload()).isEqualTo(newPayload);
        assertThat(retrievedHearing.getHearingId()).isEqualTo(hearingId);
    }

    @Test
    void delete_shouldRemoveHearing_whenHearingExists() {
        // Given
        UUID hearingId = UUID.randomUUID();
        String payload = "{\"caseId\":\"12345\",\"hearingType\":\"FINAL\"}";
        TestHearingEntity hearing = new TestHearingEntity(hearingId, payload);
        entityManager.persistAndFlush(hearing);
        entityManager.clear();

        // When
        hearingRepository.deleteById(hearingId);
        entityManager.flush();
        entityManager.clear();

        // Then
        Optional<TestHearingEntity> deletedHearing = hearingRepository.findById(hearingId);
        assertThat(deletedHearing).isEmpty();
    }

    @Test
    void findAll_shouldReturnAllHearings_whenMultipleHearingsExist() {
        // Given
        hearingRepository.deleteAll();
        UUID hearingId1 = UUID.randomUUID();
        UUID hearingId2 = UUID.randomUUID();
        UUID hearingId3 = UUID.randomUUID();

        TestHearingEntity hearing1 = new TestHearingEntity(hearingId1, "{\"caseId\":\"111\"}");
        TestHearingEntity hearing2 = new TestHearingEntity(hearingId2, "{\"caseId\":\"222\"}");
        TestHearingEntity hearing3 = new TestHearingEntity(hearingId3, "{\"caseId\":\"333\"}");

        entityManager.persist(hearing1);
        entityManager.persist(hearing2);
        entityManager.persist(hearing3);
        entityManager.flush();
        entityManager.clear();

        // When
        List<TestHearingEntity> allHearings = hearingRepository.findAll();

        // Then
        assertThat(allHearings).hasSize(3);
        assertThat(allHearings).extracting(TestHearingEntity::getHearingId)
                .containsExactlyInAnyOrder(hearingId1, hearingId2, hearingId3);
    }

}