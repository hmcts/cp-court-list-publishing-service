package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import uk.gov.hmcts.cp.domain.DtsMeta;
import uk.gov.hmcts.cp.models.transformed.CourtListDocument;
import uk.gov.hmcts.cp.openapi.model.CourtListType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.InOrder;

@ExtendWith(MockitoExtension.class)
class CaTHServiceTest {

    @Mock
    private CourtListPublisher cathPublisher;

    @Mock
    private AzureBlobService azureBlobService;

    private CaTHService cathService;

    private CourtListDocument courtListDocument;

    @BeforeEach
    void setUp() {
        cathService = new CaTHService(cathPublisher, Optional.of(azureBlobService));
        courtListDocument = CourtListDocument.builder().build();
    }

    @Test
    void sendCourtListToCaTH_shouldCallPublisherWithCorrectParameters() {
        // Given
        when(cathPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(HttpStatus.OK.value());

        // When
        LocalDate publishDate = LocalDate.of(2024, 1, 15);
        cathService.sendCourtListToCaTH(courtListDocument, CourtListType.ONLINE_PUBLIC, publishDate, null, null);

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
    void sendCourtListToCaTH_shouldUseCourtIdNumericFromPayload_whenPresent() {
        // Given - courtIdNumeric passed from payload (reference data)
        when(cathPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(HttpStatus.OK.value());

        // When
        cathService.sendCourtListToCaTH(courtListDocument, CourtListType.ONLINE_PUBLIC, LocalDate.of(2024, 1, 15), "325", null);

        // Then - DtsMeta uses courtId from payload
        ArgumentCaptor<DtsMeta> metaCaptor = ArgumentCaptor.forClass(DtsMeta.class);
        verify(cathPublisher).publish(anyString(), metaCaptor.capture());
        assertThat(metaCaptor.getValue().getCourtId()).isEqualTo("325");
    }

    @Test
    void sendCourtListToCaTH_shouldSetLanguageWelsh_whenIsWelshTrue() {
        // Given - isWelsh true passed from payload
        when(cathPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(HttpStatus.OK.value());

        // When
        cathService.sendCourtListToCaTH(courtListDocument, CourtListType.ONLINE_PUBLIC, LocalDate.of(2024, 1, 15), null, true);

        // Then - DtsMeta has language WELSH
        ArgumentCaptor<DtsMeta> metaCaptor = ArgumentCaptor.forClass(DtsMeta.class);
        verify(cathPublisher).publish(anyString(), metaCaptor.capture());
        assertThat(metaCaptor.getValue().getLanguage()).isEqualTo("WELSH");
    }

    @Test
    void sendCourtListToCaTH_shouldSetLanguageEnglish_whenIsWelshFalseOrNull() {
        // Given - isWelsh false passed from payload
        when(cathPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(HttpStatus.OK.value());

        // When
        cathService.sendCourtListToCaTH(courtListDocument, CourtListType.ONLINE_PUBLIC, LocalDate.of(2024, 1, 15), null, false);

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
        assertThatThrownBy(() -> cathService.sendCourtListToCaTH(courtListDocument, CourtListType.ONLINE_PUBLIC, LocalDate.of(2024, 1, 15), null, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to send court list document to CaTH");
    }

    @Test
    void sendCourtListToCaTH_shouldThrowException_whenGenericExceptionOccurs() {
        // Given
        when(cathPublisher.publish(anyString(), any(DtsMeta.class)))
                .thenThrow(new RuntimeException("Unexpected error"));

        // When & Then
        assertThatThrownBy(() -> cathService.sendCourtListToCaTH(courtListDocument, CourtListType.ONLINE_PUBLIC, LocalDate.of(2024, 1, 15), null, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to send court list document to CaTH");
    }

    @Test
    void sendCourtListToCaTH_shouldMapStandardListType() {
        // Given
        when(cathPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(HttpStatus.OK.value());

        // When
        cathService.sendCourtListToCaTH(courtListDocument, CourtListType.STANDARD,
            LocalDate.of(2024, 1, 15), "100", null);

        // Then
        ArgumentCaptor<DtsMeta> metaCaptor = ArgumentCaptor.forClass(DtsMeta.class);
        verify(cathPublisher).publish(anyString(), metaCaptor.capture());
        assertThat(metaCaptor.getValue().getListType()).isEqualTo("MAGISTRATES_STANDARD_LIST");
        assertThat(metaCaptor.getValue().getSensitivity()).isEqualTo("CLASSIFIED");
    }

    @Test
    void sendCourtListToCaTH_shouldThrowException_whenCourtListTypeNotSupported() {
        // When & Then
        assertThatThrownBy(() -> cathService.sendCourtListToCaTH(
                courtListDocument, CourtListType.PUBLIC, LocalDate.of(2024, 1, 15), null, null))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to send court list document to CaTH")
            .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void sendCourtListToCaTH_shouldFallbackToZero_whenCourtIdNumericIsBlank() {
        // Given
        when(cathPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(HttpStatus.OK.value());

        // When
        cathService.sendCourtListToCaTH(courtListDocument, CourtListType.ONLINE_PUBLIC,
            LocalDate.of(2024, 1, 15), "  ", null);

        // Then
        ArgumentCaptor<DtsMeta> metaCaptor = ArgumentCaptor.forClass(DtsMeta.class);
        verify(cathPublisher).publish(anyString(), metaCaptor.capture());
        assertThat(metaCaptor.getValue().getCourtId()).isEqualTo("0");
    }

    @Test
    void sendCourtListToCaTH_shouldUploadPayloadToBlobBeforePublishing() {
        // Given
        when(cathPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(HttpStatus.OK.value());

        // When
        cathService.sendCourtListToCaTH(courtListDocument, CourtListType.ONLINE_PUBLIC,
            LocalDate.of(2024, 1, 15), "325", null);

        // Then - blob upload called before publish (order verified)
        InOrder inOrder = inOrder(azureBlobService, cathPublisher);
        inOrder.verify(azureBlobService).uploadJson(anyString(), anyString());
        inOrder.verify(cathPublisher).publish(anyString(), any(DtsMeta.class));
    }

    @Test
    void sendCourtListToCaTH_shouldUploadSamePayloadToBlobAndPublisher() {
        // Given
        when(cathPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(HttpStatus.OK.value());

        // When
        cathService.sendCourtListToCaTH(courtListDocument, CourtListType.ONLINE_PUBLIC,
            LocalDate.of(2024, 1, 15), "325", null);

        // Then - same payload sent to both blob and publisher
        ArgumentCaptor<String> blobPayloadCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> publishPayloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(azureBlobService).uploadJson(blobPayloadCaptor.capture(), anyString());
        verify(cathPublisher).publish(publishPayloadCaptor.capture(), any(DtsMeta.class));
        assertThat(blobPayloadCaptor.getValue()).isEqualTo(publishPayloadCaptor.getValue());
    }

    @Test
    void sendCourtListToCaTH_shouldUploadBlobWithCorrectName() {
        // Given
        when(cathPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(HttpStatus.OK.value());

        // When
        cathService.sendCourtListToCaTH(courtListDocument, CourtListType.STANDARD,
            LocalDate.of(2024, 6, 20), "450", false);

        // Then
        ArgumentCaptor<String> blobNameCaptor = ArgumentCaptor.forClass(String.class);
        verify(azureBlobService).uploadJson(anyString(), blobNameCaptor.capture());
        String blobName = blobNameCaptor.getValue();
        assertThat(blobName).startsWith("cath-payloads/STANDARD/450/2024-06-20_");
        assertThat(blobName).endsWith(".json");
    }

    @Test
    void sendCourtListToCaTH_shouldContinuePublishing_whenBlobUploadFails() {
        // Given
        doThrow(new RuntimeException("Blob upload failed"))
            .when(azureBlobService).uploadJson(anyString(), anyString());
        when(cathPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(HttpStatus.OK.value());

        // When
        cathService.sendCourtListToCaTH(courtListDocument, CourtListType.ONLINE_PUBLIC,
            LocalDate.of(2024, 1, 15), null, null);

        // Then - publish still called despite blob failure
        verify(cathPublisher).publish(anyString(), any(DtsMeta.class));
    }

    @Test
    void sendCourtListToCaTH_shouldSkipBlobUpload_whenBlobServiceNotAvailable() {
        // Given - no blob service
        CaTHService serviceWithoutBlob = new CaTHService(cathPublisher, Optional.empty());
        when(cathPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(HttpStatus.OK.value());

        // When
        serviceWithoutBlob.sendCourtListToCaTH(courtListDocument, CourtListType.ONLINE_PUBLIC,
            LocalDate.of(2024, 1, 15), null, null);

        // Then - publish called, blob service never invoked
        verify(azureBlobService, never()).uploadJson(anyString(), anyString());
        verify(cathPublisher).publish(anyString(), any(DtsMeta.class));
    }

    @Test
    void buildBlobName_shouldCreateCorrectPathForOnlinePublic() {
        Instant timestamp = Instant.parse("2024-01-15T10:30:00Z");
        String blobName = CaTHService.buildBlobName(
            CourtListType.ONLINE_PUBLIC, "325", LocalDate.of(2024, 1, 15), timestamp);

        assertThat(blobName).isEqualTo(
            "cath-payloads/ONLINE_PUBLIC/325/2024-01-15_20240115T103000Z.json");
    }

    @Test
    void buildBlobName_shouldCreateCorrectPathForStandard() {
        Instant timestamp = Instant.parse("2024-06-20T14:45:30Z");
        String blobName = CaTHService.buildBlobName(
            CourtListType.STANDARD, "100", LocalDate.of(2024, 6, 20), timestamp);

        assertThat(blobName).isEqualTo(
            "cath-payloads/STANDARD/100/2024-06-20_20240620T144530Z.json");
    }

    @Test
    void buildBlobName_shouldUseDefaultCourtIdWhenZero() {
        Instant timestamp = Instant.parse("2024-01-15T10:30:00Z");
        String blobName = CaTHService.buildBlobName(
            CourtListType.ONLINE_PUBLIC, "0", LocalDate.of(2024, 1, 15), timestamp);

        assertThat(blobName).isEqualTo(
            "cath-payloads/ONLINE_PUBLIC/0/2024-01-15_20240115T103000Z.json");
    }

    @Test
    void testGetDisplayTo() {
        final LocalDate now = LocalDate.now();
        final String expected = CaTHService.getDisplayTo(now);
        assertThat(expected).isEqualTo(now + "T23:59:00Z");
    }
}
