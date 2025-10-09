package uk.gov.hmcts.cp.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.hmcts.cp.domain.TestHearingEntity;
import uk.gov.hmcts.cp.dto.HearingRequest;
import uk.gov.hmcts.cp.repositories.HearingRepository;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HearingServiceTest {

    @Mock
    private HearingRepository hearingRepository;

    @InjectMocks
    private HearingService hearingService;

    @Test
    void getHearingById_shouldReturnPayload_whenHearingExists() {
        // Given
        UUID hearingId = UUID.randomUUID();
        String expectedPayload = "{\"caseId\":\"12345\",\"hearingType\":\"TEST\"}";
        TestHearingEntity hearing = new TestHearingEntity(hearingId, expectedPayload);

        when(hearingRepository.findAll()).thenReturn(List.of(hearing));
        when(hearingRepository.getByHearingId(hearingId)).thenReturn(hearing);

        // When
        String result = hearingService.getHearingById(hearingId);

        // Then
        assertThat(result).isEqualTo(expectedPayload);
        verify(hearingRepository).findAll();
        verify(hearingRepository).getByHearingId(hearingId);
    }


    @Test
    void getHearingById_shouldThrowNullPointerException_whenHearingDoesNotExist() {
        // Given
        UUID hearingId = UUID.randomUUID();

        when(hearingRepository.getByHearingId(hearingId)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> hearingService.getHearingById(hearingId))
                .isInstanceOf(NullPointerException.class);

        verify(hearingRepository).getByHearingId(hearingId);
    }

    @Test
    void updateHearing_shouldUpdateExistingHearing_whenHearingExists() {
        // Given
        UUID hearingId = UUID.randomUUID();
        String originalPayload = "{\"caseId\":\"12345\",\"hearingType\":\"TEST\"}";
        String newPayload = "{\"caseId\":\"12345\",\"hearingType\":\"TEST UPDATED\"}";

        TestHearingEntity existingHearing = new TestHearingEntity(hearingId, originalPayload);
        HearingRequest request = new HearingRequest(hearingId, newPayload);

        when(hearingRepository.getByHearingId(hearingId)).thenReturn(existingHearing);
        when(hearingRepository.save(existingHearing)).thenReturn(existingHearing);

        // When
        String result = hearingService.updateHearing(request);

        // Then
        assertThat(result).isEqualTo(newPayload);
        assertThat(existingHearing.getPayload()).isEqualTo(newPayload);
        verify(hearingRepository).getByHearingId(hearingId);
        verify(hearingRepository).save(existingHearing);
    }

    @Test
    void updateHearing_shouldCreateNewHearing_whenHearingDoesNotExist() {
        // Given
        UUID hearingId = UUID.randomUUID();
        String payload = "{\"caseId\":\"12345\",\"hearingType\":\"TEST\"}";
        HearingRequest request = new HearingRequest(hearingId, payload);

        when(hearingRepository.getByHearingId(hearingId)).thenReturn(null);
        when(hearingRepository.save(any(TestHearingEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        String result = hearingService.updateHearing(request);

        // Then
        assertThat(result).isEqualTo(payload);
        verify(hearingRepository).getByHearingId(hearingId);
        verify(hearingRepository).save(any(TestHearingEntity.class));
    }

}