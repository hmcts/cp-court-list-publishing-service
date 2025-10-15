package uk.gov.hmcts.cp.services;

import uk.gov.hmcts.cp.dto.azure.FileInfo;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AzureBlobService {

    private final BlobContainerClient blobContainerClient;

    /**
     * Upload file
     */
    public String uploadFile(MultipartFile file, String folder) throws IOException {
        String fileName = file.getOriginalFilename();
        String blobPath = folder.isEmpty() ? fileName : folder + "/" + fileName;

        BlobClient blobClient = blobContainerClient.getBlobClient(blobPath);
        blobClient.upload(file.getInputStream(), file.getSize(), true);

        return blobClient.getBlobUrl();
    }

    /**
     * Get file
     */
    public byte[] downloadFile(String filePath) throws IOException {
        BlobClient blobClient = blobContainerClient.getBlobClient(filePath);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        blobClient.downloadStream(outputStream);

        return outputStream.toByteArray();
    }

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

    /**
     * List folders
     */
    public List<String> listFolders(String folder) {
        List<String> folders = new ArrayList<>();
        String prefix = folder.isEmpty() ? "" : folder + "/";

        for (BlobItem item : blobContainerClient.listBlobsByHierarchy(prefix)) {
            if (item.isPrefix()) {
                String folderName = item.getName().replace(prefix, "").replace("/", "");
                folders.add(folderName);
            }
        }

        return folders;
    }

    private String extractFileName(String fullPath) {
        int lastSlash = fullPath.lastIndexOf('/');
        return lastSlash >= 0 ? fullPath.substring(lastSlash + 1) : fullPath;
    }
}