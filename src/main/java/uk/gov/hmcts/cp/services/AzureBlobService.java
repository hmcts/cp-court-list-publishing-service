package uk.gov.hmcts.cp.services;

import com.azure.storage.blob.BlobContainerClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "azure.storage.enabled", havingValue = "true", matchIfMissing = false)
public class AzureBlobService {

    private final BlobContainerClient blobContainerClient;
}