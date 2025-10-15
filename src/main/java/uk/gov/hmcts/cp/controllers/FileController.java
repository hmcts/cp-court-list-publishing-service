package uk.gov.hmcts.cp.controllers;

import uk.gov.hmcts.cp.dto.azure.FileInfo;
import uk.gov.hmcts.cp.services.AzureBlobService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
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
