package uk.gov.hmcts.cp.controllers;

import uk.gov.hmcts.cp.openapi.api.FilesApi;
import uk.gov.hmcts.cp.openapi.model.FileInfo;
import uk.gov.hmcts.cp.services.AzureBlobService;
import uk.gov.hmcts.cp.services.CourtListPublisherBlobClientService;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "azure.storage.enabled", havingValue = "true", matchIfMissing = false)
public class FileController implements FilesApi {

    private final AzureBlobService blobService;
    private final CourtListPublisherBlobClientService courtListPublisherBlobService;

    /**
     * List files in folder
     * GET /api/files/list?folder=documents
     */
    @Override
    public ResponseEntity<List<FileInfo>> listFiles(
            @RequestParam(value = "folder", defaultValue = "") final String folder) {
        final List<FileInfo> files = blobService.listFiles(folder);
        return ResponseEntity.ok()
                .contentType(new MediaType("application", "vnd.courtlistpublishing-service.files+json"))
                .body(files);
    }

    /**
     * Download file by ID - streams {fileId}.pdf for large file support
     * GET /api/files/download/{fileId}
     */
    @Override
    public ResponseEntity<Resource> downloadFile(@PathVariable("fileId") final UUID fileId) {
        Optional<InputStream> streamOpt = courtListPublisherBlobService.openPdfDownloadStream(fileId);
        if (streamOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new InputStreamResource(streamOpt.get());
        String filename = fileId + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }
}
