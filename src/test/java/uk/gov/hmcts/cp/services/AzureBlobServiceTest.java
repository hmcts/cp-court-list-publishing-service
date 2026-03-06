package uk.gov.hmcts.cp.services;

import com.azure.storage.blob.BlobContainerClient;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AzureBlobServiceTest {

    @Mock
    private BlobContainerClient blobContainerClient;

    @InjectMocks
    private AzureBlobService azureBlobService;
}
