package uk.gov.hmcts.cp.services;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AzureBlobServiceTest {

    @Mock
    private BlobContainerClient blobContainerClient;

    @InjectMocks
    private AzureBlobService azureBlobService;

    @Test
    void uploadJson_shouldUploadWithCorrectBlobNameAndContentType() {
        String payload = "{\"key\":\"value\"}";
        String blobName = "cath-payloads/ONLINE_PUBLIC/325/2024-01-15_20240115T103000Z.json";
        BlobClient mockBlobClient = mock(BlobClient.class);

        when(blobContainerClient.getBlobClient(blobName)).thenReturn(mockBlobClient);

        azureBlobService.uploadJson(payload, blobName);

        verify(mockBlobClient).upload(any(InputStream.class), eq((long) payload.getBytes().length), eq(true));
        ArgumentCaptor<BlobHttpHeaders> headersCaptor = ArgumentCaptor.forClass(BlobHttpHeaders.class);
        verify(mockBlobClient).setHttpHeaders(headersCaptor.capture());
        assertThat(headersCaptor.getValue().getContentType()).isEqualTo("application/json");
    }

    @Test
    void uploadJson_shouldRequestCorrectBlobName() {
        String blobName = "cath-payloads/STANDARD/100/2024-06-20_20240620T144530Z.json";
        BlobClient mockBlobClient = mock(BlobClient.class);

        when(blobContainerClient.getBlobClient(blobName)).thenReturn(mockBlobClient);

        azureBlobService.uploadJson("{}", blobName);

        verify(blobContainerClient).getBlobClient(blobName);
    }

    @Test
    void uploadJson_shouldUploadCorrectByteLength() {
        String payload = "{\"courtList\":\"data with unicode: \u00e9\u00e0\u00fc\"}";
        String blobName = "cath-payloads/test.json";
        BlobClient mockBlobClient = mock(BlobClient.class);

        when(blobContainerClient.getBlobClient(blobName)).thenReturn(mockBlobClient);

        azureBlobService.uploadJson(payload, blobName);

        long expectedLength = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        verify(mockBlobClient).upload(any(InputStream.class), eq(expectedLength), eq(true));
    }

    @Test
    void uploadJson_shouldThrowWhenUploadFails() {
        String blobName = "cath-payloads/test.json";
        BlobClient mockBlobClient = mock(BlobClient.class);

        when(blobContainerClient.getBlobClient(blobName)).thenReturn(mockBlobClient);
        doThrow(new RuntimeException("Upload failed"))
            .when(mockBlobClient).upload(any(InputStream.class), any(Long.class), eq(true));

        assertThatThrownBy(() -> azureBlobService.uploadJson("{}", blobName))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Azure storage error")
            .hasMessageContaining(blobName);
    }

    @Test
    void uploadJson_shouldThrowWhenSetHeadersFails() {
        String blobName = "cath-payloads/test.json";
        BlobClient mockBlobClient = mock(BlobClient.class);

        when(blobContainerClient.getBlobClient(blobName)).thenReturn(mockBlobClient);
        doThrow(new RuntimeException("Headers failed"))
            .when(mockBlobClient).setHttpHeaders(any(BlobHttpHeaders.class));

        assertThatThrownBy(() -> azureBlobService.uploadJson("{}", blobName))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Azure storage error")
            .hasMessageContaining(blobName);
    }

    @Test
    void uploadJson_shouldOverwriteExistingBlob() {
        String blobName = "cath-payloads/test.json";
        BlobClient mockBlobClient = mock(BlobClient.class);

        when(blobContainerClient.getBlobClient(blobName)).thenReturn(mockBlobClient);

        azureBlobService.uploadJson("{}", blobName);

        // third argument (overwrite) should be true
        verify(mockBlobClient).upload(any(InputStream.class), any(Long.class), eq(true));
    }
}
