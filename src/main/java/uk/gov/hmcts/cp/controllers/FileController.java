package uk.gov.hmcts.cp.controllers;

import uk.gov.hmcts.cp.dto.azure.FileInfo;
import uk.gov.hmcts.cp.services.AzureBlobService;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "azure.storage.enabled", havingValue = "true", matchIfMissing = false)
public class FileController {

    private final AzureBlobService blobService;

    /**
     * List files in folder
     * GET /api/files/list?folder=documents
     */
    @GetMapping("/list")
    public ResponseEntity<List<FileInfo>> listFiles(
            @RequestParam(value = "folder", defaultValue = "") String folder) {
        List<FileInfo> files = blobService.listFiles(folder);
        return ResponseEntity.ok(files);
    }
}
