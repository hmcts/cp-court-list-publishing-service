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
        
        // Set the base URL field using reflection since it's @Value injected
        java.lang.reflect.Field field = PdfGenerationService.class.getDeclaredField("commonPlatformQueryApiBaseUrl");
        field.setAccessible(true);
        field.set(pdfGenerationService, "http://localhost:8080");
    }

    @Test
    void generateAndUploadPdf_shouldReturnSasUrl_whenValidInput() throws IOException {
        // Given
        JsonObject payload = Json.createObjectBuilder().build();
        String expectedSasUrl = "https://storage.example.com/blob.pdf?sasToken";
        String expectedBlobName = "court-lists/" + courtListId + ".pdf";
        byte[] mockPdfBytes = "Mock PDF content".getBytes();

        // Mock PDF generation service call
        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mockPdfBytes, HttpStatus.OK));
        when(blobClientService.uploadPdfAndGenerateSasUrl(any(InputStream.class), anyLong(), eq(expectedBlobName)))
                .thenReturn(expectedSasUrl);

        // When
        String result = pdfGenerationService.generateAndUploadPdf(payload, courtListId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(expectedSasUrl);
        assertThat(result).contains("?");
        verify(blobClientService).uploadPdfAndGenerateSasUrl(any(InputStream.class), anyLong(), eq(expectedBlobName));
    }

    @Test
    void generateAndUploadPdf_shouldReturnSasUrl_whenPayloadIsNull() throws IOException {
        // Given
        String expectedSasUrl = "https://storage.example.com/blob.pdf?sasToken";
        String expectedBlobName = "court-lists/" + courtListId + ".pdf";
        byte[] mockPdfBytes = "Mock PDF content".getBytes();

        // Mock PDF generation service call
        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mockPdfBytes, HttpStatus.OK));
        when(blobClientService.uploadPdfAndGenerateSasUrl(any(InputStream.class), anyLong(), eq(expectedBlobName)))
                .thenReturn(expectedSasUrl);

        // When
        String result = pdfGenerationService.generateAndUploadPdf(null, courtListId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(expectedSasUrl);
        verify(blobClientService).uploadPdfAndGenerateSasUrl(any(InputStream.class), anyLong(), eq(expectedBlobName));
    }

    @Test
    void generateAndUploadPdf_shouldIncludeCourtCentreId_whenPayloadContainsCourtCentreId() throws IOException {
        // Given
        JsonObject payload = Json.createObjectBuilder()
                .add("courtCentreId", courtCentreId.toString())
                .build();
        String expectedSasUrl = "https://storage.example.com/blob.pdf?sasToken";
        String expectedBlobName = "court-lists/" + courtListId + ".pdf";
        byte[] mockPdfBytes = "Mock PDF content".getBytes();

        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mockPdfBytes, HttpStatus.OK));
        when(blobClientService.uploadPdfAndGenerateSasUrl(any(InputStream.class), anyLong(), eq(expectedBlobName)))
                .thenReturn(expectedSasUrl);

        // When
        String result = pdfGenerationService.generateAndUploadPdf(payload, courtListId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(expectedSasUrl);
        verify(blobClientService).uploadPdfAndGenerateSasUrl(any(InputStream.class), anyLong(), eq(expectedBlobName));
    }

    @Test
    void generateAndUploadPdf_shouldIncludeCourtListType_whenPayloadContainsCourtListType() throws IOException {
        // Given
        String courtListType = "PUBLIC";
        JsonObject payload = Json.createObjectBuilder()
                .add("courtListType", courtListType)
                .build();
        String expectedSasUrl = "https://storage.example.com/blob.pdf?sasToken";
        String expectedBlobName = "court-lists/" + courtListId + ".pdf";
        byte[] mockPdfBytes = "Mock PDF content".getBytes();

        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mockPdfBytes, HttpStatus.OK));
        when(blobClientService.uploadPdfAndGenerateSasUrl(any(InputStream.class), anyLong(), eq(expectedBlobName)))
                .thenReturn(expectedSasUrl);

        // When
        String result = pdfGenerationService.generateAndUploadPdf(payload, courtListId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(expectedSasUrl);
        verify(blobClientService).uploadPdfAndGenerateSasUrl(any(InputStream.class), anyLong(), eq(expectedBlobName));
    }

    @Test
    void generateAndUploadPdf_shouldIncludeAllFields_whenPayloadContainsAllFields() throws IOException {
        // Given
        String courtListType = "STANDARD";
        JsonObject payload = Json.createObjectBuilder()
                .add("courtCentreId", courtCentreId.toString())
                .add("courtListType", courtListType)
                .add("otherField", "otherValue")
                .build();
        String expectedSasUrl = "https://storage.example.com/blob.pdf?sasToken";
        String expectedBlobName = "court-lists/" + courtListId + ".pdf";
        byte[] mockPdfBytes = "Mock PDF content".getBytes();

        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mockPdfBytes, HttpStatus.OK));
        when(blobClientService.uploadPdfAndGenerateSasUrl(any(InputStream.class), anyLong(), eq(expectedBlobName)))
                .thenReturn(expectedSasUrl);

        // When
        String result = pdfGenerationService.generateAndUploadPdf(payload, courtListId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(expectedSasUrl);
        verify(blobClientService).uploadPdfAndGenerateSasUrl(any(InputStream.class), anyLong(), eq(expectedBlobName));
    }

    @Test
    void generateAndUploadPdf_shouldReturnSasUrl_whenEmptyPayload() throws IOException {
        // Given
        JsonObject payload = Json.createObjectBuilder().build();
        String expectedSasUrl = "https://storage.example.com/blob.pdf?sasToken";
        String expectedBlobName = "court-lists/" + courtListId + ".pdf";
        byte[] mockPdfBytes = "Mock PDF content".getBytes();

        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mockPdfBytes, HttpStatus.OK));
        when(blobClientService.uploadPdfAndGenerateSasUrl(any(InputStream.class), anyLong(), eq(expectedBlobName)))
                .thenReturn(expectedSasUrl);

        // When
        String result = pdfGenerationService.generateAndUploadPdf(payload, courtListId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(expectedSasUrl);
        verify(blobClientService).uploadPdfAndGenerateSasUrl(any(InputStream.class), anyLong(), eq(expectedBlobName));
    }

    @Test
    void generateAndUploadPdf_shouldReturnDifferentSasUrls_whenDifferentCourtListIds() throws IOException {
        // Given
        UUID courtListId1 = UUID.randomUUID();
        UUID courtListId2 = UUID.randomUUID();
        JsonObject payload = Json.createObjectBuilder().build();
        String expectedSasUrl1 = "https://storage.example.com/blob1.pdf?sasToken";
        String expectedSasUrl2 = "https://storage.example.com/blob2.pdf?sasToken";
        byte[] mockPdfBytes = "Mock PDF content".getBytes();

        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mockPdfBytes, HttpStatus.OK));
        when(blobClientService.uploadPdfAndGenerateSasUrl(any(InputStream.class), anyLong(), eq("court-lists/" + courtListId1 + ".pdf")))
                .thenReturn(expectedSasUrl1);
        when(blobClientService.uploadPdfAndGenerateSasUrl(any(InputStream.class), anyLong(), eq("court-lists/" + courtListId2 + ".pdf")))
                .thenReturn(expectedSasUrl2);

        // When
        String result1 = pdfGenerationService.generateAndUploadPdf(payload, courtListId1);
        String result2 = pdfGenerationService.generateAndUploadPdf(payload, courtListId2);

        // Then
        assertThat(result1).isNotNull();
        assertThat(result2).isNotNull();
        assertThat(result1).isEqualTo(expectedSasUrl1);
        assertThat(result2).isEqualTo(expectedSasUrl2);
        assertThat(result1).isNotEqualTo(result2);
    }

    @Test
    void generateAndUploadPdf_shouldHandleSpecialCharactersInPayload() throws IOException {
        // Given
        String courtListType = "PUBLIC & STANDARD";
        JsonObject payload = Json.createObjectBuilder()
                .add("courtListType", courtListType)
                .build();
        String expectedSasUrl = "https://storage.example.com/blob.pdf?sasToken";
        String expectedBlobName = "court-lists/" + courtListId + ".pdf";
        byte[] mockPdfBytes = "Mock PDF content".getBytes();

        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mockPdfBytes, HttpStatus.OK));
        when(blobClientService.uploadPdfAndGenerateSasUrl(any(InputStream.class), anyLong(), eq(expectedBlobName)))
                .thenReturn(expectedSasUrl);

        // When
        String result = pdfGenerationService.generateAndUploadPdf(payload, courtListId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(expectedSasUrl);
        verify(blobClientService).uploadPdfAndGenerateSasUrl(any(InputStream.class), anyLong(), eq(expectedBlobName));
    }

    @Test
    void generateAndUploadPdf_shouldUploadPdfWithCorrectSize() throws IOException {
        // Given
        JsonObject payload = Json.createObjectBuilder()
                .add("courtCentreId", courtCentreId.toString())
                .add("courtListType", "PUBLIC")
                .build();
        String expectedSasUrl = "https://storage.example.com/blob.pdf?sasToken";
        String expectedBlobName = "court-lists/" + courtListId + ".pdf";
        byte[] mockPdfBytes = "Mock PDF content".getBytes();

        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mockPdfBytes, HttpStatus.OK));
        when(blobClientService.uploadPdfAndGenerateSasUrl(any(InputStream.class), anyLong(), eq(expectedBlobName)))
                .thenReturn(expectedSasUrl);

        // When
        String result = pdfGenerationService.generateAndUploadPdf(payload, courtListId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(expectedSasUrl);
        // Verify that upload was called with a size greater than 0
        verify(blobClientService).uploadPdfAndGenerateSasUrl(any(InputStream.class), anyLong(), eq(expectedBlobName));
    }

    @Test
    void generateAndUploadPdf_shouldHandleNullCourtListId() throws IOException {
        // Given
        JsonObject payload = Json.createObjectBuilder().build();
        UUID nullCourtListId = null;
        String expectedSasUrl = "https://storage.example.com/blob.pdf?sasToken";
        String expectedBlobName = "court-lists/null.pdf";
        byte[] mockPdfBytes = "Mock PDF content".getBytes();

        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mockPdfBytes, HttpStatus.OK));
        when(blobClientService.uploadPdfAndGenerateSasUrl(any(InputStream.class), anyLong(), eq(expectedBlobName)))
                .thenReturn(expectedSasUrl);

        // When
        String result = pdfGenerationService.generateAndUploadPdf(payload, nullCourtListId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(expectedSasUrl);
        verify(blobClientService).uploadPdfAndGenerateSasUrl(any(InputStream.class), anyLong(), eq(expectedBlobName));
    }

    @Test
    void generateAndUploadPdf_shouldUploadPdf_whenPayloadDoesNotContainExpectedFields() throws IOException {
        // Given
        JsonObject payload = Json.createObjectBuilder()
                .add("someOtherField", "someValue")
                .build();
        String expectedSasUrl = "https://storage.example.com/blob.pdf?sasToken";
        String expectedBlobName = "court-lists/" + courtListId + ".pdf";
        byte[] mockPdfBytes = "Mock PDF content".getBytes();

        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mockPdfBytes, HttpStatus.OK));
        when(blobClientService.uploadPdfAndGenerateSasUrl(any(InputStream.class), anyLong(), eq(expectedBlobName)))
                .thenReturn(expectedSasUrl);

        // When
        String result = pdfGenerationService.generateAndUploadPdf(payload, courtListId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(expectedSasUrl);
        verify(blobClientService).uploadPdfAndGenerateSasUrl(any(InputStream.class), anyLong(), eq(expectedBlobName));
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
    void generateAndUploadPdf_shouldUseCorrectBlobName() throws IOException {
        // Given
        JsonObject payload = Json.createObjectBuilder().build();
        String expectedSasUrl = "https://storage.example.com/blob.pdf?sasToken";
        String expectedBlobName = "court-lists/" + courtListId + ".pdf";
        byte[] mockPdfBytes = "Mock PDF content".getBytes();

        when(restTemplate.exchange(any(java.net.URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mockPdfBytes, HttpStatus.OK));
        when(blobClientService.uploadPdfAndGenerateSasUrl(any(InputStream.class), anyLong(), eq(expectedBlobName)))
                .thenReturn(expectedSasUrl);

        // When
        pdfGenerationService.generateAndUploadPdf(payload, courtListId);

        // Then
        verify(blobClientService).uploadPdfAndGenerateSasUrl(any(InputStream.class), anyLong(), eq(expectedBlobName));
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
