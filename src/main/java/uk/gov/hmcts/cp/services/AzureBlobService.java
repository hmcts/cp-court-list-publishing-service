package uk.gov.hmcts.cp.services;

import uk.gov.hmcts.cp.dto.azure.FileInfo;

import java.util.ArrayList;
import java.util.List;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AzureBlobService {

    private final BlobContainerClient blobContainerClient;


    /**
     * List files in folder
     */
    public List<FileInfo> listFiles(String folder) {
        List<FileInfo> files = new ArrayList<>();
        String prefix = folder.isEmpty() ? "" : folder + "/";

        for (BlobItem item : blobContainerClient.listBlobsByHierarchy(prefix)) {
            if (!item.isPrefix()) {
                BlobClient blobClient = blobContainerClient.getBlobClient(item.getName());

                files.add(new FileInfo(
                        extractFileName(item.getName()),
                        item.getName(),
                        blobClient.getBlobUrl(),
                        item.getProperties().getContentLength()
                ));
            }
        }

        return files;
    }


    private String extractFileName(String fullPath) {
        int lastSlash = fullPath.lastIndexOf('/');
        return lastSlash >= 0 ? fullPath.substring(lastSlash + 1) : fullPath;
    }
}