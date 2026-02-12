package uk.gov.hmcts.cp.controllers;

import uk.gov.hmcts.cp.openapi.api.FilesApi;
import uk.gov.hmcts.cp.openapi.model.FileInfo;
import uk.gov.hmcts.cp.services.AzureBlobService;
import uk.gov.hmcts.cp.services.CourtListPublisherBlobClientService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
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
     * Download file by ID - loads full {fileId}.pdf into memory and returns as Resource in a single shot
     * GET /api/files/download/{fileId}
     */
    @Override
    public ResponseEntity<Resource> downloadFile(
            @PathVariable("fileId") final UUID fileId,
            @RequestHeader(value = "Accept", required = true) final String accept) {
        Optional<byte[]> bytesOpt = courtListPublisherBlobService.downloadPdf(fileId);
        if (bytesOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new ByteArrayResource(bytesOpt.get());
        String filename = fileId + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }
}
