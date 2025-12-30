package uk.gov.hmcts.cp.controllers;

import uk.gov.hmcts.cp.openapi.api.FilesApi;
import uk.gov.hmcts.cp.openapi.model.FileInfo;
import uk.gov.hmcts.cp.services.AzureBlobService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "azure.storage.enabled", havingValue = "true", matchIfMissing = false)
public class FileController implements FilesApi {

    private final AzureBlobService blobService;

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
}
