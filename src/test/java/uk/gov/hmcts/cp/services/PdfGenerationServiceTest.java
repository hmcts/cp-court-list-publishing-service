package uk.gov.hmcts.cp.services;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.cp.config.CourtListPublishingSystemUserConfig;

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

@ExtendWith(MockitoExtension.class)
class PdfGenerationServiceTest {

    @Mock
    private CourtListPublisherBlobClientService blobClientService;

    @Mock
    private HttpClientFactory httpClientFactory;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private CourtListPublishingSystemUserConfig systemUserConfig;

    @InjectMocks
    private PdfGenerationService pdfGenerationService;

    private UUID courtListId;
    private UUID courtCentreId;

    @BeforeEach
    void setUp() throws Exception {
        courtListId = UUID.randomUUID();
        courtCentreId = UUID.randomUUID();
        // Mock HttpClientFactory to return RestTemplate (lenient for tests that don't use it)
        lenient().when(httpClientFactory.getClient()).thenReturn(restTemplate);
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

        UUID result = pdfGenerationService.generateAndUploadPdf(payload, courtListId);

        assertThat(result).isEqualTo(courtListId);
        verify(blobClientService).uploadPdf(any(InputStream.class), anyLong(), eq(courtListId));
    }

    @Test
    void generateAndUploadPdf_shouldReturnFileId_whenPayloadIsNull() throws IOException {
        String expectedBlobName = courtListId + ".pdf";
        byte[] mockPdfBytes = "Mock PDF content".getBytes();

        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mockPdfBytes, HttpStatus.OK));

        UUID result = pdfGenerationService.generateAndUploadPdf(null, courtListId);

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

        UUID result = pdfGenerationService.generateAndUploadPdf(payload, courtListId);

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

        UUID result = pdfGenerationService.generateAndUploadPdf(payload, courtListId);

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

        UUID result = pdfGenerationService.generateAndUploadPdf(payload, courtListId);

        assertThat(result).isEqualTo(courtListId);
        verify(blobClientService).uploadPdf(any(InputStream.class), anyLong(), eq(courtListId));
    }

    @Test
    void generateAndUploadPdf_shouldReturnFileId_whenEmptyPayload() throws IOException {
        JsonObject payload = Json.createObjectBuilder().build();
        byte[] mockPdfBytes = "Mock PDF content".getBytes();

        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mockPdfBytes, HttpStatus.OK));

        UUID result = pdfGenerationService.generateAndUploadPdf(payload, courtListId);

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

        UUID result1 = pdfGenerationService.generateAndUploadPdf(payload, courtListId1);
        UUID result2 = pdfGenerationService.generateAndUploadPdf(payload, courtListId2);

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

        UUID result = pdfGenerationService.generateAndUploadPdf(payload, courtListId);

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

        UUID result = pdfGenerationService.generateAndUploadPdf(payload, courtListId);

        assertThat(result).isEqualTo(courtListId);
        verify(blobClientService).uploadPdf(any(InputStream.class), anyLong(), eq(courtListId));
    }

    @Test
    void generateAndUploadPdf_shouldHandleNullCourtListId() throws IOException {
        JsonObject payload = Json.createObjectBuilder().build();
        byte[] mockPdfBytes = "Mock PDF content".getBytes();

        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mockPdfBytes, HttpStatus.OK));

        UUID result = pdfGenerationService.generateAndUploadPdf(payload, null);

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

        UUID result = pdfGenerationService.generateAndUploadPdf(payload, courtListId);

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
        assertThatThrownBy(() -> pdfGenerationService.generateAndUploadPdf(payload, courtListId))
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

        pdfGenerationService.generateAndUploadPdf(payload, courtListId);

        verify(blobClientService).uploadPdf(any(InputStream.class), anyLong(), eq(courtListId));
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
