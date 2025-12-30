package uk.gov.hmcts.cp.services;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobItemProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.hmcts.cp.openapi.model.FileInfo;

@ExtendWith(MockitoExtension.class)
class AzureBlobServiceTest {

    @Mock
    private BlobContainerClient blobContainerClient;

    @InjectMocks
    private AzureBlobService azureBlobService;

    @Test
    void listFiles_shouldReturnEmptyList_whenNoFilesExist() {
        // Given
        String folder = "documents";
        String prefix = "documents/";

        when(blobContainerClient.listBlobsByHierarchy(prefix)).thenReturn(
                new MockPagedIterable<>(List.of())
        );

        // When
        List<FileInfo> result = azureBlobService.listFiles(folder);

        // Then
        assertThat(result).isEmpty();
        verify(blobContainerClient).listBlobsByHierarchy(prefix);
    }

    @Test
    void listFiles_shouldReturnFileList_whenFilesExist() {
        // Given
        String folder = "documents";
        String prefix = "documents/";

        BlobItem blobItem1 = createMockBlobItem("documents/file1.pdf", false, 1024L);
        BlobItem blobItem2 = createMockBlobItem("documents/file2.docx", false, 2048L);

        BlobClient mockBlobClient1 = mock(BlobClient.class);
        BlobClient mockBlobClient2 = mock(BlobClient.class);

        when(blobContainerClient.listBlobsByHierarchy(prefix)).thenReturn(
                new MockPagedIterable<>(Arrays.asList(blobItem1, blobItem2))
        );
        when(blobContainerClient.getBlobClient("documents/file1.pdf")).thenReturn(mockBlobClient1);
        when(blobContainerClient.getBlobClient("documents/file2.docx")).thenReturn(mockBlobClient2);
        when(mockBlobClient1.getBlobUrl()).thenReturn("https://storage.example.com/file1.pdf");
        when(mockBlobClient2.getBlobUrl()).thenReturn("https://storage.example.com/file2.docx");

        // When
        List<FileInfo> result = azureBlobService.listFiles(folder);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("file1.pdf");
        assertThat(result.get(0).getPath()).isEqualTo("documents/file1.pdf");
        assertThat(result.get(0).getUrl().toString()).isEqualTo("https://storage.example.com/file1.pdf");
        assertThat(result.get(0).getSize()).isEqualTo(1024L);
        assertThat(result.get(1).getName()).isEqualTo("file2.docx");
        assertThat(result.get(1).getPath()).isEqualTo("documents/file2.docx");
        assertThat(result.get(1).getSize()).isEqualTo(2048L);
    }

    @Test
    void listFiles_shouldUseEmptyPrefix_whenFolderIsEmpty() {
        // Given
        String folder = "";
        String prefix = "";

        BlobItem blobItem = createMockBlobItem("rootfile.txt", false, 256L);
        BlobClient mockBlobClient = mock(BlobClient.class);

        when(blobContainerClient.listBlobsByHierarchy(prefix)).thenReturn(
                new MockPagedIterable<>(List.of(blobItem))
        );
        when(blobContainerClient.getBlobClient("rootfile.txt")).thenReturn(mockBlobClient);
        when(mockBlobClient.getBlobUrl()).thenReturn("https://storage.example.com/rootfile.txt");

        // When
        List<FileInfo> result = azureBlobService.listFiles(folder);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("rootfile.txt");
        assertThat(result.get(0).getPath()).isEqualTo("rootfile.txt");
    }


    private BlobItem createMockBlobItem(String name, boolean isPrefix, long size) {
        BlobItem blobItem = mock(BlobItem.class);
        BlobItemProperties properties = mock(BlobItemProperties.class);

        when(blobItem.getName()).thenReturn(name);
        when(blobItem.isPrefix()).thenReturn(isPrefix);
        when(blobItem.getProperties()).thenReturn(properties);
        when(properties.getContentLength()).thenReturn(size);

        return blobItem;
    }

    private static class MockPagedIterable<T> extends com.azure.core.http.rest.PagedIterable<T> {
        private final List<T> items;

        public MockPagedIterable(List<T> items) {
            super(new com.azure.core.http.rest.PagedFlux<>(() ->
                    Mono.just(new com.azure.core.http.rest.PagedResponseBase<>(
                            null,  // HttpRequest
                            200,   // statusCode
                            null,  // HttpHeaders
                            items, // items
                            null,  // continuationToken
                            null   // deserializedHeaders
                    ))
            ));
            this.items = items;
        }
        @Override
        public java.util.stream.Stream<T> stream() {
            return items.stream();
        }

        @Override
        public java.util.Iterator<T> iterator() {
            return items.iterator();
        }

    }
}
