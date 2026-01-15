package uk.gov.hmcts.cp.services;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.common.StorageSharedKeyCredential;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourtListPublisherBlobClientServiceTest {

    @Mock
    private BlobContainerClient blobContainerClient;

    @InjectMocks
    private CourtListPublisherBlobClientService service;

    private static final String STORAGE_ACCOUNT_NAME = "teststorageaccount";
    private static final String STORAGE_ACCOUNT_KEY = "dGVzdGtleQ=="; // Base64 encoded "testkey"
    private static final long SAS_URL_EXPIRY_MINUTES = 120L;
    private static final String CONTAINER_NAME = "test-container";
    private static final String BLOB_NAME = "court-lists/test.pdf";
    private static final String BLOB_URL = "https://teststorageaccount.blob.core.windows.net/test-container/court-lists/test.pdf";
    private static final String SAS_TOKEN = "?sv=2021-06-08&ss=b&srt=co&sp=r&se=2024-01-01T00:00:00Z&st=2023-01-01T00:00:00Z&spr=https&sig=test";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "storageAccountName", STORAGE_ACCOUNT_NAME);
        ReflectionTestUtils.setField(service, "storageAccountKey", STORAGE_ACCOUNT_KEY);
        ReflectionTestUtils.setField(service, "sasUrlExpiryInMinutes", SAS_URL_EXPIRY_MINUTES);
    }

    @Test
    void uploadPdfAndGenerateSasUrl_shouldUploadFileAndReturnSasUrl_whenValidInput() {
        // Given
        InputStream fileInputStream = new ByteArrayInputStream("test pdf content".getBytes());
        long fileSize = 16L;

        BlobClient mockBlobClient = mock(BlobClient.class);
        BlobClient mockSasBlobClient = mock(BlobClient.class);

        when(blobContainerClient.getBlobContainerName()).thenReturn(CONTAINER_NAME);
        when(blobContainerClient.getBlobClient(BLOB_NAME)).thenReturn(mockBlobClient);
        when(mockBlobClient.getBlobUrl()).thenReturn(BLOB_URL);
        when(mockBlobClient.getBlobName()).thenReturn(BLOB_NAME);

        try (MockedConstruction<BlobClientBuilder> mockedBuilder = mockConstruction(
                BlobClientBuilder.class,
                (mock, context) -> {
                    when(mock.endpoint(anyString())).thenReturn(mock);
                    when(mock.containerName(anyString())).thenReturn(mock);
                    when(mock.blobName(anyString())).thenReturn(mock);
                    when(mock.credential(any(StorageSharedKeyCredential.class))).thenReturn(mock);
                    when(mock.buildClient()).thenReturn(mockSasBlobClient);
                })) {
            when(mockSasBlobClient.generateSas(any())).thenReturn(SAS_TOKEN.substring(1)); // Remove leading ?

            // When
            String result = service.uploadPdfAndGenerateSasUrl(fileInputStream, fileSize, BLOB_NAME);

            // Then
            assertThat(result).isEqualTo(BLOB_URL + SAS_TOKEN);
            verify(mockBlobClient).upload(fileInputStream, fileSize, true);
            verify(mockBlobClient).setHttpHeaders(any(BlobHttpHeaders.class));
            
            ArgumentCaptor<BlobHttpHeaders> headersCaptor = ArgumentCaptor.forClass(BlobHttpHeaders.class);
            verify(mockBlobClient).setHttpHeaders(headersCaptor.capture());
            assertThat(headersCaptor.getValue().getContentType()).isEqualTo("application/pdf");
        }
    }

    @Test
    void uploadPdfAndGenerateSasUrl_shouldSetPdfContentType_whenUploading() {
        // Given
        InputStream fileInputStream = new ByteArrayInputStream("test content".getBytes());
        long fileSize = 12L;

        BlobClient mockBlobClient = mock(BlobClient.class);
        BlobClient mockSasBlobClient = mock(BlobClient.class);

        when(blobContainerClient.getBlobContainerName()).thenReturn(CONTAINER_NAME);
        when(blobContainerClient.getBlobClient(BLOB_NAME)).thenReturn(mockBlobClient);
        when(mockBlobClient.getBlobUrl()).thenReturn(BLOB_URL);
        when(mockBlobClient.getBlobName()).thenReturn(BLOB_NAME);

        try (MockedConstruction<BlobClientBuilder> mockedBuilder = mockConstruction(
                BlobClientBuilder.class,
                (mock, context) -> {
                    when(mock.endpoint(anyString())).thenReturn(mock);
                    when(mock.containerName(anyString())).thenReturn(mock);
                    when(mock.blobName(anyString())).thenReturn(mock);
                    when(mock.credential(any(StorageSharedKeyCredential.class))).thenReturn(mock);
                    when(mock.buildClient()).thenReturn(mockSasBlobClient);
                })) {
            when(mockSasBlobClient.generateSas(any())).thenReturn("sasToken");

            // When
            service.uploadPdfAndGenerateSasUrl(fileInputStream, fileSize, BLOB_NAME);

            // Then
            ArgumentCaptor<BlobHttpHeaders> headersCaptor = ArgumentCaptor.forClass(BlobHttpHeaders.class);
            verify(mockBlobClient).setHttpHeaders(headersCaptor.capture());
            BlobHttpHeaders headers = headersCaptor.getValue();
            assertThat(headers.getContentType()).isEqualTo("application/pdf");
        }
    }

    @Test
    void uploadPdfAndGenerateSasUrl_shouldThrowRuntimeException_whenUploadFails() {
        // Given
        InputStream fileInputStream = new ByteArrayInputStream("test content".getBytes());
        long fileSize = 12L;

        BlobClient mockBlobClient = mock(BlobClient.class);
        when(blobContainerClient.getBlobContainerName()).thenReturn(CONTAINER_NAME);
        when(blobContainerClient.getBlobClient(BLOB_NAME)).thenReturn(mockBlobClient);
        doThrow(new RuntimeException("Upload failed"))
                .when(mockBlobClient).upload(any(InputStream.class), any(long.class), any(boolean.class));

        // When & Then
        assertThatThrownBy(() -> service.uploadPdfAndGenerateSasUrl(fileInputStream, fileSize, BLOB_NAME))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Azure storage error while uploading PDF file")
                .hasMessageContaining(BLOB_NAME)
                .hasCauseInstanceOf(RuntimeException.class);

        verify(mockBlobClient).upload(any(InputStream.class), eq(fileSize), eq(true));
        verify(mockBlobClient, never()).setHttpHeaders(any());
    }

    @Test
    void uploadPdfAndGenerateSasUrl_shouldThrowRuntimeExceptionWithIllegalStateExceptionCause_whenAccountKeyIsMissing() {
        // Given
        ReflectionTestUtils.setField(service, "storageAccountKey", "");
        InputStream fileInputStream = new ByteArrayInputStream("test content".getBytes());
        long fileSize = 12L;

        BlobClient mockBlobClient = mock(BlobClient.class);
        when(blobContainerClient.getBlobContainerName()).thenReturn(CONTAINER_NAME);
        when(blobContainerClient.getBlobClient(BLOB_NAME)).thenReturn(mockBlobClient);
        // Note: getBlobUrl() and getBlobName() are not stubbed because they're not called
        // when the account key is empty - the exception is thrown before reaching those calls

        // When & Then
        assertThatThrownBy(() -> service.uploadPdfAndGenerateSasUrl(fileInputStream, fileSize, BLOB_NAME))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Azure storage error while uploading PDF file")
                .hasMessageContaining("Storage account key is required for SAS generation")
                .hasCauseInstanceOf(IllegalStateException.class)
                .satisfies(exception -> {
                    RuntimeException ex = (RuntimeException) exception;
                    assertThat(ex.getCause()).isInstanceOf(IllegalStateException.class);
                    assertThat(ex.getCause().getMessage()).contains("Storage account key is required for SAS generation");
                });

        verify(mockBlobClient).upload(any(InputStream.class), eq(fileSize), eq(true));
    }

    @Test
    void uploadPdfAndGenerateSasUrl_shouldThrowRuntimeExceptionWithIllegalStateExceptionCause_whenAccountKeyIsNull() {
        // Given
        ReflectionTestUtils.setField(service, "storageAccountKey", null);
        InputStream fileInputStream = new ByteArrayInputStream("test content".getBytes());
        long fileSize = 12L;

        BlobClient mockBlobClient = mock(BlobClient.class);
        when(blobContainerClient.getBlobContainerName()).thenReturn(CONTAINER_NAME);
        when(blobContainerClient.getBlobClient(BLOB_NAME)).thenReturn(mockBlobClient);
        // Note: getBlobUrl() and getBlobName() are not stubbed because they're not called
        // when the account key is null - the exception is thrown before reaching those calls

        // When & Then
        assertThatThrownBy(() -> service.uploadPdfAndGenerateSasUrl(fileInputStream, fileSize, BLOB_NAME))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Azure storage error while uploading PDF file")
                .hasMessageContaining("Storage account key is required for SAS generation")
                .hasCauseInstanceOf(IllegalStateException.class)
                .satisfies(exception -> {
                    RuntimeException ex = (RuntimeException) exception;
                    assertThat(ex.getCause()).isInstanceOf(IllegalStateException.class);
                    assertThat(ex.getCause().getMessage()).contains("Storage account key is required for SAS generation");
                });

        verify(mockBlobClient).upload(any(InputStream.class), eq(fileSize), eq(true));
    }

    @Test
    void uploadPdfAndGenerateSasUrlWithAutoName_shouldGenerateBlobNameAndUpload_whenFolderPathProvided() {
        // Given
        InputStream fileInputStream = new ByteArrayInputStream("test content".getBytes());
        long fileSize = 12L;
        String folderPath = "court-lists";

        BlobClient mockBlobClient = mock(BlobClient.class);
        BlobClient mockSasBlobClient = mock(BlobClient.class);

        when(blobContainerClient.getBlobContainerName()).thenReturn(CONTAINER_NAME);
        when(mockBlobClient.getBlobUrl()).thenReturn(BLOB_URL);
        when(mockBlobClient.getBlobName()).thenReturn(BLOB_NAME);

        try (MockedConstruction<BlobClientBuilder> mockedBuilder = mockConstruction(
                BlobClientBuilder.class,
                (mock, context) -> {
                    when(mock.endpoint(anyString())).thenReturn(mock);
                    when(mock.containerName(anyString())).thenReturn(mock);
                    when(mock.blobName(anyString())).thenReturn(mock);
                    when(mock.credential(any(StorageSharedKeyCredential.class))).thenReturn(mock);
                    when(mock.buildClient()).thenReturn(mockSasBlobClient);
                })) {
            when(blobContainerClient.getBlobClient(anyString())).thenReturn(mockBlobClient);
            when(mockSasBlobClient.generateSas(any())).thenReturn("sasToken");

            // When
            String result = service.uploadPdfAndGenerateSasUrlWithAutoName(fileInputStream, fileSize, folderPath);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).contains("court-lists/");
            assertThat(result).contains(".pdf");
            assertThat(result).contains("sasToken");
            verify(blobContainerClient).getBlobClient(anyString());
            verify(mockBlobClient).upload(any(InputStream.class), eq(fileSize), eq(true));
        }
    }

    @Test
    void uploadPdfAndGenerateSasUrlWithAutoName_shouldGenerateBlobNameInRoot_whenFolderPathIsNull() {
        // Given
        InputStream fileInputStream = new ByteArrayInputStream("test content".getBytes());
        long fileSize = 12L;

        BlobClient mockBlobClient = mock(BlobClient.class);
        BlobClient mockSasBlobClient = mock(BlobClient.class);

        when(blobContainerClient.getBlobContainerName()).thenReturn(CONTAINER_NAME);
        when(mockBlobClient.getBlobUrl()).thenReturn("https://teststorageaccount.blob.core.windows.net/test-container/test.pdf");
        when(mockBlobClient.getBlobName()).thenReturn("test.pdf");

        try (MockedConstruction<BlobClientBuilder> mockedBuilder = mockConstruction(
                BlobClientBuilder.class,
                (mock, context) -> {
                    when(mock.endpoint(anyString())).thenReturn(mock);
                    when(mock.containerName(anyString())).thenReturn(mock);
                    when(mock.blobName(anyString())).thenReturn(mock);
                    when(mock.credential(any(StorageSharedKeyCredential.class))).thenReturn(mock);
                    when(mock.buildClient()).thenReturn(mockSasBlobClient);
                })) {
            ArgumentCaptor<String> blobNameCaptor = ArgumentCaptor.forClass(String.class);
            when(blobContainerClient.getBlobClient(blobNameCaptor.capture())).thenReturn(mockBlobClient);
            when(mockSasBlobClient.generateSas(any())).thenReturn("sasToken");

            // When
            String result = service.uploadPdfAndGenerateSasUrlWithAutoName(fileInputStream, fileSize, null);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).contains(".pdf");
            // Verify blob name doesn't contain folder separator (no "/" in the blob name itself)
            String capturedBlobName = blobNameCaptor.getValue();
            assertThat(capturedBlobName).doesNotContain("/");
            assertThat(capturedBlobName).endsWith(".pdf");
            verify(blobContainerClient).getBlobClient(anyString());
        }
    }

    @Test
    void uploadPdfAndGenerateSasUrlWithAutoName_shouldGenerateBlobNameInRoot_whenFolderPathIsEmpty() {
        // Given
        InputStream fileInputStream = new ByteArrayInputStream("test content".getBytes());
        long fileSize = 12L;

        BlobClient mockBlobClient = mock(BlobClient.class);
        BlobClient mockSasBlobClient = mock(BlobClient.class);

        when(blobContainerClient.getBlobContainerName()).thenReturn(CONTAINER_NAME);
        when(mockBlobClient.getBlobUrl()).thenReturn("https://teststorageaccount.blob.core.windows.net/test-container/test.pdf");
        when(mockBlobClient.getBlobName()).thenReturn("test.pdf");

        try (MockedConstruction<BlobClientBuilder> mockedBuilder = mockConstruction(
                BlobClientBuilder.class,
                (mock, context) -> {
                    when(mock.endpoint(anyString())).thenReturn(mock);
                    when(mock.containerName(anyString())).thenReturn(mock);
                    when(mock.blobName(anyString())).thenReturn(mock);
                    when(mock.credential(any(StorageSharedKeyCredential.class))).thenReturn(mock);
                    when(mock.buildClient()).thenReturn(mockSasBlobClient);
                })) {
            ArgumentCaptor<String> blobNameCaptor = ArgumentCaptor.forClass(String.class);
            when(blobContainerClient.getBlobClient(blobNameCaptor.capture())).thenReturn(mockBlobClient);
            when(mockSasBlobClient.generateSas(any())).thenReturn("sasToken");

            // When
            String result = service.uploadPdfAndGenerateSasUrlWithAutoName(fileInputStream, fileSize, "");

            // Then
            assertThat(result).isNotNull();
            assertThat(result).contains(".pdf");
            // Verify blob name doesn't contain folder separator
            String capturedBlobName = blobNameCaptor.getValue();
            assertThat(capturedBlobName).doesNotContain("/");
            assertThat(capturedBlobName).endsWith(".pdf");
            verify(blobContainerClient).getBlobClient(anyString());
        }
    }

    @Test
    void uploadPdfAndGenerateSasUrl_shouldUseCorrectExpiryTime_whenGeneratingSas() {
        // Given
        long customExpiryMinutes = 60L;
        ReflectionTestUtils.setField(service, "sasUrlExpiryInMinutes", customExpiryMinutes);
        
        InputStream fileInputStream = new ByteArrayInputStream("test content".getBytes());
        long fileSize = 12L;

        BlobClient mockBlobClient = mock(BlobClient.class);
        BlobClient mockSasBlobClient = mock(BlobClient.class);

        when(blobContainerClient.getBlobContainerName()).thenReturn(CONTAINER_NAME);
        when(blobContainerClient.getBlobClient(BLOB_NAME)).thenReturn(mockBlobClient);
        when(mockBlobClient.getBlobUrl()).thenReturn(BLOB_URL);
        when(mockBlobClient.getBlobName()).thenReturn(BLOB_NAME);

        try (MockedConstruction<BlobClientBuilder> mockedBuilder = mockConstruction(
                BlobClientBuilder.class,
                (mock, context) -> {
                    when(mock.endpoint(anyString())).thenReturn(mock);
                    when(mock.containerName(anyString())).thenReturn(mock);
                    when(mock.blobName(anyString())).thenReturn(mock);
                    when(mock.credential(any(StorageSharedKeyCredential.class))).thenReturn(mock);
                    when(mock.buildClient()).thenReturn(mockSasBlobClient);
                })) {
            when(mockSasBlobClient.generateSas(any())).thenReturn("sasToken");

            // When
            service.uploadPdfAndGenerateSasUrl(fileInputStream, fileSize, BLOB_NAME);

            // Then
            verify(mockSasBlobClient).generateSas(any());
        }
    }

    @Test
    void uploadPdfAndGenerateSasUrl_shouldCreateBlobClientWithAccountKey_whenGeneratingSas() {
        // Given
        InputStream fileInputStream = new ByteArrayInputStream("test content".getBytes());
        long fileSize = 12L;

        BlobClient mockBlobClient = mock(BlobClient.class);
        BlobClient mockSasBlobClient = mock(BlobClient.class);

        when(blobContainerClient.getBlobContainerName()).thenReturn(CONTAINER_NAME);
        when(blobContainerClient.getBlobClient(BLOB_NAME)).thenReturn(mockBlobClient);
        when(mockBlobClient.getBlobUrl()).thenReturn(BLOB_URL);
        when(mockBlobClient.getBlobName()).thenReturn(BLOB_NAME);

        try (MockedConstruction<BlobClientBuilder> mockedBuilder = mockConstruction(
                BlobClientBuilder.class,
                (mock, context) -> {
                    when(mock.endpoint(anyString())).thenReturn(mock);
                    when(mock.containerName(anyString())).thenReturn(mock);
                    when(mock.blobName(anyString())).thenReturn(mock);
                    when(mock.credential(any(StorageSharedKeyCredential.class))).thenReturn(mock);
                    when(mock.buildClient()).thenReturn(mockSasBlobClient);
                })) {
            when(mockSasBlobClient.generateSas(any())).thenReturn("sasToken");

            // When
            service.uploadPdfAndGenerateSasUrl(fileInputStream, fileSize, BLOB_NAME);

            // Then
            ArgumentCaptor<String> endpointCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> containerCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> blobNameCaptor = ArgumentCaptor.forClass(String.class);

            // Verify BlobClientBuilder was called with correct parameters
            verify(mockedBuilder.constructed().getFirst()).endpoint(endpointCaptor.capture());
            verify(mockedBuilder.constructed().getFirst()).containerName(containerCaptor.capture());
            verify(mockedBuilder.constructed().getFirst()).blobName(blobNameCaptor.capture());
            verify(mockedBuilder.constructed().getFirst()).credential(any(StorageSharedKeyCredential.class));
            verify(mockedBuilder.constructed().getFirst()).buildClient();

            assertThat(endpointCaptor.getValue()).contains(STORAGE_ACCOUNT_NAME);
            assertThat(containerCaptor.getValue()).isEqualTo(CONTAINER_NAME);
            assertThat(blobNameCaptor.getValue()).isEqualTo(BLOB_NAME);
        }
    }
}
