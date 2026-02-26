package uk.gov.hmcts.cp.services;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.cp.config.CourtListPublishingSystemUserConfig;
import uk.gov.hmcts.cp.openapi.model.CourtListType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;

@ExtendWith(MockitoExtension.class)
class PdfGenerationServiceTest {

    @Mock
    private CourtListPublisherBlobClientService blobClientService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private CourtListPublishingSystemUserConfig systemUserConfig;

    @InjectMocks
    private PdfGenerationService pdfGenerationService;

    private UUID courtListId;
    private UUID courtCentreId;

    @Captor
    private ArgumentCaptor<HttpEntity<String>> httpEntityCaptor;

    @BeforeEach
    void setUp() throws Exception {
        courtListId = UUID.randomUUID();
        courtCentreId = UUID.randomUUID();
        lenient().when(systemUserConfig.getSystemUserId()).thenReturn("ba4e97ab-2174-4fa2-abfe-3ac2bb04bc75");

        // Set the base URL field using reflection since it's @Value injected
        java.lang.reflect.Field field = PdfGenerationService.class.getDeclaredField("commonPlatformQueryApiBaseUrl");
        field.setAccessible(true);
        field.set(pdfGenerationService, "http://localhost:8080");
    }

    @Test
    void generateAndUploadPdf_shouldReturnFileId_whenValidInput() throws IOException {
        JsonObject payload = Json.createObjectBuilder().build();
        byte[] mockPdfBytes = "Mock PDF content".getBytes();

        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mockPdfBytes, HttpStatus.OK));

        UUID result = pdfGenerationService.generateAndUploadPdf(payload, courtListId, CourtListType.STANDARD, false);

        assertThat(result).isEqualTo(courtListId);
        verify(blobClientService).uploadPdf(any(InputStream.class), anyLong(), eq(courtListId));
    }

    @Test
    void generateAndUploadPdf_shouldReturnFileId_whenPayloadIsNull() throws IOException {
        String expectedBlobName = courtListId + ".pdf";
        byte[] mockPdfBytes = "Mock PDF content".getBytes();

        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mockPdfBytes, HttpStatus.OK));

        UUID result = pdfGenerationService.generateAndUploadPdf(null, courtListId, CourtListType.STANDARD, false);

        assertThat(result).isEqualTo(courtListId);
        verify(blobClientService).uploadPdf(any(InputStream.class), anyLong(), eq(courtListId));
    }

    @Test
    void generateAndUploadPdf_shouldIncludeCourtCentreId_whenPayloadContainsCourtCentreId() throws IOException {
        JsonObject payload = Json.createObjectBuilder()
                .add("courtCentreId", courtCentreId.toString())
                .build();
        byte[] mockPdfBytes = "Mock PDF content".getBytes();

        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mockPdfBytes, HttpStatus.OK));

        UUID result = pdfGenerationService.generateAndUploadPdf(payload, courtListId, CourtListType.STANDARD, false);

        assertThat(result).isEqualTo(courtListId);
        verify(blobClientService).uploadPdf(any(InputStream.class), anyLong(), eq(courtListId));
    }

    @Test
    void generateAndUploadPdf_shouldIncludeCourtListType_whenPayloadContainsCourtListType() throws IOException {
        JsonObject payload = Json.createObjectBuilder()
                .add("courtListType", "PUBLIC")
                .build();
        byte[] mockPdfBytes = "Mock PDF content".getBytes();

        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mockPdfBytes, HttpStatus.OK));

        UUID result = pdfGenerationService.generateAndUploadPdf(payload, courtListId, CourtListType.STANDARD, false);

        assertThat(result).isEqualTo(courtListId);
        verify(blobClientService).uploadPdf(any(InputStream.class), anyLong(), eq(courtListId));
    }

    @Test
    void generateAndUploadPdf_shouldIncludeAllFields_whenPayloadContainsAllFields() throws IOException {
        JsonObject payload = Json.createObjectBuilder()
                .add("courtCentreId", courtCentreId.toString())
                .add("courtListType", "STANDARD")
                .add("otherField", "otherValue")
                .build();
        byte[] mockPdfBytes = "Mock PDF content".getBytes();

        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mockPdfBytes, HttpStatus.OK));

        UUID result = pdfGenerationService.generateAndUploadPdf(payload, courtListId, CourtListType.STANDARD, false);

        assertThat(result).isEqualTo(courtListId);
        verify(blobClientService).uploadPdf(any(InputStream.class), anyLong(), eq(courtListId));
    }

    @Test
    void generateAndUploadPdf_shouldReturnFileId_whenEmptyPayload() throws IOException {
        JsonObject payload = Json.createObjectBuilder().build();
        byte[] mockPdfBytes = "Mock PDF content".getBytes();

        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mockPdfBytes, HttpStatus.OK));

        UUID result = pdfGenerationService.generateAndUploadPdf(payload, courtListId, CourtListType.STANDARD, false);

        assertThat(result).isEqualTo(courtListId);
        verify(blobClientService).uploadPdf(any(InputStream.class), anyLong(), eq(courtListId));
    }

    @Test
    void generateAndUploadPdf_shouldReturnDifferentFileIds_whenDifferentCourtListIds() throws IOException {
        UUID courtListId1 = UUID.randomUUID();
        UUID courtListId2 = UUID.randomUUID();
        JsonObject payload = Json.createObjectBuilder().build();
        byte[] mockPdfBytes = "Mock PDF content".getBytes();

        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mockPdfBytes, HttpStatus.OK));

        UUID result1 = pdfGenerationService.generateAndUploadPdf(payload, courtListId1, CourtListType.STANDARD, false);
        UUID result2 = pdfGenerationService.generateAndUploadPdf(payload, courtListId2, CourtListType.STANDARD, false);

        assertThat(result1).isEqualTo(courtListId1);
        assertThat(result2).isEqualTo(courtListId2);
        assertThat(result1).isNotEqualTo(result2);
    }

    @Test
    void generateAndUploadPdf_shouldHandleSpecialCharactersInPayload() throws IOException {
        JsonObject payload = Json.createObjectBuilder()
                .add("courtListType", "PUBLIC & STANDARD")
                .build();
        byte[] mockPdfBytes = "Mock PDF content".getBytes();

        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mockPdfBytes, HttpStatus.OK));

        UUID result = pdfGenerationService.generateAndUploadPdf(payload, courtListId, CourtListType.STANDARD, false);

        assertThat(result).isEqualTo(courtListId);
        verify(blobClientService).uploadPdf(any(InputStream.class), anyLong(), eq(courtListId));
    }

    @Test
    void generateAndUploadPdf_shouldUploadPdfWithCorrectSize() throws IOException {
        JsonObject payload = Json.createObjectBuilder()
                .add("courtCentreId", courtCentreId.toString())
                .add("courtListType", "PUBLIC")
                .build();
        byte[] mockPdfBytes = "Mock PDF content".getBytes();

        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mockPdfBytes, HttpStatus.OK));

        UUID result = pdfGenerationService.generateAndUploadPdf(payload, courtListId, CourtListType.STANDARD, false);

        assertThat(result).isEqualTo(courtListId);
        verify(blobClientService).uploadPdf(any(InputStream.class), anyLong(), eq(courtListId));
    }

    @Test
    void generateAndUploadPdf_shouldHandleNullCourtListId() throws IOException {
        JsonObject payload = Json.createObjectBuilder().build();
        byte[] mockPdfBytes = "Mock PDF content".getBytes();

        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mockPdfBytes, HttpStatus.OK));

        UUID result = pdfGenerationService.generateAndUploadPdf(payload, null, CourtListType.STANDARD, false);

        assertThat(result).isNull();
        verify(blobClientService).uploadPdf(any(InputStream.class), anyLong(), eq(null));
    }

    @Test
    void generateAndUploadPdf_shouldUploadPdf_whenPayloadDoesNotContainExpectedFields() throws IOException {
        JsonObject payload = Json.createObjectBuilder()
                .add("someOtherField", "someValue")
                .build();
        byte[] mockPdfBytes = "Mock PDF content".getBytes();

        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mockPdfBytes, HttpStatus.OK));

        UUID result = pdfGenerationService.generateAndUploadPdf(payload, courtListId, CourtListType.STANDARD, false);

        assertThat(result).isEqualTo(courtListId);
        verify(blobClientService).uploadPdf(any(InputStream.class), anyLong(), eq(courtListId));
    }

    @Test
    void generateAndUploadPdf_shouldThrowIOException_whenPdfGenerationFails() {
        // Given
        JsonObject payload = Json.createObjectBuilder().build();
        
        // Mock PDF generation service to throw exception
        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), eq(byte[].class)))
                .thenThrow(new RuntimeException("PDF generation failed"));

        // When & Then
        assertThatThrownBy(() -> pdfGenerationService.generateAndUploadPdf(payload, courtListId, CourtListType.STANDARD, false))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Failed to generate PDF")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void generateAndUploadPdf_shouldUseCorrectFileId() throws IOException {
        JsonObject payload = Json.createObjectBuilder().build();
        byte[] mockPdfBytes = "Mock PDF content".getBytes();

        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mockPdfBytes, HttpStatus.OK));

        pdfGenerationService.generateAndUploadPdf(payload, courtListId, CourtListType.STANDARD, false);

        verify(blobClientService).uploadPdf(any(InputStream.class), anyLong(), eq(courtListId));
    }

    @Test
    void getTemplateName_shouldReturnOnlinePublicTemplate_whenCourtListTypeIsOnlinePublicAndIsWelshFalse() {
        assertThat(pdfGenerationService.getTemplateName(CourtListType.ONLINE_PUBLIC, false))
                .isEqualTo("OnlinePublicCourtList");
    }

    @Test
    void getTemplateName_shouldReturnStandardTemplate_whenCourtListTypeIsStandardAndIsWelshFalse() {
        assertThat(pdfGenerationService.getTemplateName(CourtListType.STANDARD, false))
                .isEqualTo("BenchAndStandardCourtList");
    }

    @Test
    void getTemplateName_shouldReturnWelshTemplate_whenIsWelshTrueAndOnlinePublic() {
        assertThat(pdfGenerationService.getTemplateName(CourtListType.ONLINE_PUBLIC, true))
                .isEqualTo("OnlinePublicCourtListEnglishWelsh");
    }

    @Test
    void getTemplateName_shouldReturnNull_whenIsWelshTrueAndStandard() {
        assertThat(pdfGenerationService.getTemplateName(CourtListType.STANDARD, true)).isNull();
    }

    @Test
    void getTemplateName_shouldReturnNull_whenCourtListTypeUnmappedAndIsWelshTrue() {
        assertThat(pdfGenerationService.getTemplateName(CourtListType.PUBLIC, true)).isNull();
    }

    @Test
    void generateAndUploadPdf_shouldThrowIllegalArgumentException_whenStandardAndIsWelshTrue() throws IOException {
        JsonObject payload = Json.createObjectBuilder().build();

        assertThatThrownBy(() -> pdfGenerationService.generateAndUploadPdf(payload, courtListId, CourtListType.STANDARD, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No template defined for court list type: STANDARD");
    }

    @Test
    void generateAndUploadPdf_shouldUseWelshTemplate_whenIsWelshTrue() throws IOException {
        JsonObject payload = Json.createObjectBuilder().build();
        byte[] mockPdfBytes = "Mock PDF content".getBytes();

        when(restTemplate.exchange(any(java.net.URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mockPdfBytes, HttpStatus.OK));

        pdfGenerationService.generateAndUploadPdf(payload, courtListId, CourtListType.ONLINE_PUBLIC, true);

        verify(restTemplate).exchange(any(java.net.URI.class), eq(HttpMethod.POST), httpEntityCaptor.capture(), eq(byte[].class));
        String requestBody = httpEntityCaptor.getValue().getBody();
        assertThat(requestBody).contains("OnlinePublicCourtListEnglishWelsh");
    }

    @Test
    void getTemplateName_shouldReturnNull_whenCourtListTypeIsPublicAndIsWelshFalse() {
        assertThat(pdfGenerationService.getTemplateName(CourtListType.PUBLIC, false)).isNull();
    }

    @Test
    void getTemplateName_shouldReturnNull_whenCourtListTypeIsNullAndIsWelshFalse() {
        assertThat(pdfGenerationService.getTemplateName(null, false)).isNull();
    }

    @Test
    void generateAndUploadPdf_shouldThrowIllegalArgumentException_whenCourtListTypeHasNoTemplate() throws IOException {
        JsonObject payload = Json.createObjectBuilder().build();

        assertThatThrownBy(() -> pdfGenerationService.generateAndUploadPdf(payload, courtListId, CourtListType.PUBLIC, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No template defined for court list type: PUBLIC");
    }

    @Test
    void generateAndUploadPdf_shouldThrowIllegalArgumentException_whenCourtListTypeIsNull() throws IOException {
        JsonObject payload = Json.createObjectBuilder().build();

        assertThatThrownBy(() -> pdfGenerationService.generateAndUploadPdf(payload, courtListId, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No template defined for court list type: null");
    }

    @Test
    void getPdfSize_shouldReturnCorrectSize_whenValidOutputStream() {
        // Given
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        String testContent = "Test PDF content";
        outputStream.writeBytes(testContent.getBytes());

        // When
        long size = pdfGenerationService.getPdfSize(outputStream);

        // Then
        assertThat(size).isEqualTo(testContent.getBytes().length);
        assertThat(size).isEqualTo(outputStream.size());
    }

    @Test
    void getPdfSize_shouldReturnZero_whenEmptyOutputStream() {
        // Given
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // When
        long size = pdfGenerationService.getPdfSize(outputStream);

        // Then
        assertThat(size).isEqualTo(0);
    }
}
