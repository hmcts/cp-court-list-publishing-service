package uk.gov.hmcts.cp.services;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.specialized.BlobInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourtListPublisherBlobClientServiceTest {

    @Mock
    private BlobContainerClient blobContainerClient;

    @InjectMocks
    private CourtListPublisherBlobClientService service;

    private static final UUID FILE_ID = UUID.fromString("3be64c15-0988-41c8-8345-6fef4218f5eb");
    private static final String EXPECTED_BLOB_NAME = "3be64c15-0988-41c8-8345-6fef4218f5eb.pdf";

    @Test
    void uploadPdf_shouldUploadWithCorrectBlobNameAndContentType() {
        InputStream fileInputStream = new ByteArrayInputStream("test pdf content".getBytes());
        long fileSize = 16L;
        BlobClient mockBlobClient = mock(BlobClient.class);

        when(blobContainerClient.getBlobContainerName()).thenReturn("test-container");
        when(blobContainerClient.getBlobClient(EXPECTED_BLOB_NAME)).thenReturn(mockBlobClient);

        service.uploadPdf(fileInputStream, fileSize, FILE_ID);

        verify(mockBlobClient).upload(fileInputStream, fileSize, true);
        ArgumentCaptor<BlobHttpHeaders> headersCaptor = ArgumentCaptor.forClass(BlobHttpHeaders.class);
        verify(mockBlobClient).setHttpHeaders(headersCaptor.capture());
        assertThat(headersCaptor.getValue().getContentType()).isEqualTo("application/pdf");
        verify(blobContainerClient).getBlobClient(EXPECTED_BLOB_NAME);
    }

    @Test
    void uploadPdf_shouldThrowWhenUploadFails() {
        InputStream fileInputStream = new ByteArrayInputStream("test".getBytes());
        BlobClient mockBlobClient = mock(BlobClient.class);

        when(blobContainerClient.getBlobContainerName()).thenReturn("container");
        when(blobContainerClient.getBlobClient(any())).thenReturn(mockBlobClient);
        doThrow(new RuntimeException("Upload failed"))
                .when(mockBlobClient).upload(any(InputStream.class), eq(4L), eq(true));

        assertThatThrownBy(() -> service.uploadPdf(fileInputStream, 4L, FILE_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Azure storage error")
                .hasMessageContaining(FILE_ID + ".pdf");
    }

    @Test
    void openPdfStream_shouldReturnInputStream_whenBlobExists() {
        BlobClient mockBlobClient = mock(BlobClient.class);
        BlobInputStream mockStream = mock(BlobInputStream.class);

        when(blobContainerClient.getBlobClient(EXPECTED_BLOB_NAME)).thenReturn(mockBlobClient);
        when(mockBlobClient.exists()).thenReturn(true);
        when(mockBlobClient.openInputStream()).thenReturn(mockStream);

        Optional<InputStream> result = service.openPdfStream(FILE_ID);

        assertThat(result).isPresent();
        assertThat(result.get()).isSameAs(mockStream);
        verify(blobContainerClient).getBlobClient(EXPECTED_BLOB_NAME);
        verify(mockBlobClient).exists();
        verify(mockBlobClient).openInputStream();
    }

    @Test
    void openPdfStream_shouldReturnEmpty_whenBlobDoesNotExist() {
        BlobClient mockBlobClient = mock(BlobClient.class);

        when(blobContainerClient.getBlobClient(EXPECTED_BLOB_NAME)).thenReturn(mockBlobClient);
        when(mockBlobClient.exists()).thenReturn(false);

        Optional<InputStream> result = service.openPdfStream(FILE_ID);

        assertThat(result).isEmpty();
        verify(blobContainerClient).getBlobClient(EXPECTED_BLOB_NAME);
        verify(mockBlobClient).exists();
        verify(mockBlobClient, never()).openInputStream();
    }

    @Test
    void openPdfStream_shouldThrow_whenBlobClientErrors() {
        BlobClient mockBlobClient = mock(BlobClient.class);

        when(blobContainerClient.getBlobClient(EXPECTED_BLOB_NAME)).thenReturn(mockBlobClient);
        when(mockBlobClient.exists()).thenThrow(new RuntimeException("Blob client error"));

        assertThatThrownBy(() -> service.openPdfStream(FILE_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Azure storage error")
                .hasMessageContaining(FILE_ID + ".pdf");
    }
}
