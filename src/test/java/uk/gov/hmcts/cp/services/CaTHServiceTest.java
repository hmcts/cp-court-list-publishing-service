package uk.gov.hmcts.cp.services;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import uk.gov.hmcts.cp.domain.DtsMeta;
import uk.gov.hmcts.cp.models.transformed.CourtListDocument;
import uk.gov.hmcts.cp.openapi.model.CourtListType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import java.time.LocalDate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaTHServiceTest {

    @Mock
    private CourtListPublisher cathPublisher;

    @InjectMocks
    private CaTHService cathService;

    private CourtListDocument courtListDocument;

    @BeforeEach
    void setUp() {
        courtListDocument = CourtListDocument.builder().build();
    }

    @Test
    void sendCourtListToCaTH_shouldCallPublisherWithCorrectParameters() {
        // Given
        when(cathPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(HttpStatus.OK.value());

        // When
        LocalDate publishDate = LocalDate.of(2024, 1, 15);
        cathService.sendCourtListToCaTH(courtListDocument, CourtListType.ONLINE_PUBLIC, publishDate);

        // Then - Verify CaTHPublisher was called
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<DtsMeta> metaCaptor = ArgumentCaptor.forClass(DtsMeta.class);
        
        verify(cathPublisher).publish(payloadCaptor.capture(), metaCaptor.capture());

        // Verify payload contains the court list document
        String capturedPayload = payloadCaptor.getValue();
        assertThat(capturedPayload).isNotNull().isNotEmpty();

        // Verify metadata is set correctly
        DtsMeta capturedMeta = metaCaptor.getValue();
        assertThat(capturedMeta).isNotNull();
        assertThat(capturedMeta.getProvenance()).isEqualTo("COMMON_PLATFORM");
        assertThat(capturedMeta.getType()).isEqualTo("LIST");
        // When document has no courtIdNumeric, DtsMeta uses fallback "0"
        assertThat(capturedMeta.getCourtId()).isEqualTo("0");
        assertThat(capturedMeta.getDisplayTo()).isEqualTo("2024-01-15T23:59:00Z");
        // When isWelsh is null or false, language is ENGLISH
        assertThat(capturedMeta.getLanguage()).isEqualTo("ENGLISH");
    }

    @Test
    void sendCourtListToCaTH_shouldUseCourtIdNumericFromDocument_whenPresent() {
        // Given - document with reference data courtId (from getCourtCenterDataByCourtName)
        courtListDocument = CourtListDocument.builder()
                .courtIdNumeric("325")
                .build();
        when(cathPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(HttpStatus.OK.value());

        // When
        cathService.sendCourtListToCaTH(courtListDocument, CourtListType.ONLINE_PUBLIC, LocalDate.of(2024, 1, 15));

        // Then - DtsMeta uses courtId from reference data
        ArgumentCaptor<DtsMeta> metaCaptor = ArgumentCaptor.forClass(DtsMeta.class);
        verify(cathPublisher).publish(anyString(), metaCaptor.capture());
        assertThat(metaCaptor.getValue().getCourtId()).isEqualTo("325");
    }

    @Test
    void sendCourtListToCaTH_shouldSetLanguageWelsh_whenIsWelshTrue() {
        // Given - document with isWelsh true
        courtListDocument = CourtListDocument.builder()
                .isWelsh(true)
                .build();
        when(cathPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(HttpStatus.OK.value());

        // When
        cathService.sendCourtListToCaTH(courtListDocument, CourtListType.ONLINE_PUBLIC, LocalDate.of(2024, 1, 15));

        // Then - DtsMeta has language WELSH
        ArgumentCaptor<DtsMeta> metaCaptor = ArgumentCaptor.forClass(DtsMeta.class);
        verify(cathPublisher).publish(anyString(), metaCaptor.capture());
        assertThat(metaCaptor.getValue().getLanguage()).isEqualTo("WELSH");
    }

    @Test
    void sendCourtListToCaTH_shouldSetLanguageEnglish_whenIsWelshFalseOrNull() {
        // Given - document with isWelsh false
        courtListDocument = CourtListDocument.builder()
                .isWelsh(false)
                .build();
        when(cathPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(HttpStatus.OK.value());

        // When
        cathService.sendCourtListToCaTH(courtListDocument, CourtListType.ONLINE_PUBLIC, LocalDate.of(2024, 1, 15));

        // Then - DtsMeta has language ENGLISH
        ArgumentCaptor<DtsMeta> metaCaptor = ArgumentCaptor.forClass(DtsMeta.class);
        verify(cathPublisher).publish(anyString(), metaCaptor.capture());
        assertThat(metaCaptor.getValue().getLanguage()).isEqualTo("ENGLISH");
    }

    @Test
    void sendCourtListToCaTH_shouldThrowException_whenPublisherThrowsException() {
        // Given
        when(cathPublisher.publish(anyString(), any(DtsMeta.class)))
                .thenThrow(new RuntimeException("Publishing failed"));

        // When & Then
        assertThatThrownBy(() -> cathService.sendCourtListToCaTH(courtListDocument, CourtListType.ONLINE_PUBLIC, LocalDate.of(2024, 1, 15)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to send court list document to CaTH");
    }

    @Test
    void sendCourtListToCaTH_shouldThrowException_whenGenericExceptionOccurs() {
        // Given
        when(cathPublisher.publish(anyString(), any(DtsMeta.class)))
                .thenThrow(new RuntimeException("Unexpected error"));

        // When & Then
        assertThatThrownBy(() -> cathService.sendCourtListToCaTH(courtListDocument, CourtListType.ONLINE_PUBLIC, LocalDate.of(2024, 1, 15)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to send court list document to CaTH");
    }

    @Test
    void testGetDisplayTo() {
        final LocalDate now = LocalDate.now();
        final String expected = CaTHService.getDisplayTo(now);
        assertThat(expected).isEqualTo(now + "T23:59:00Z");
    }
}
