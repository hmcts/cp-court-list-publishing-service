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
     * Upload file
     * POST /api/files/upload?folder=documents
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "") String folder) {
        try {
            String url = blobService.uploadFile(file, folder);
            return ResponseEntity.ok(Map.of(
                    "message", "Upload successful",
                    "url", url
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Download file
     * GET /api/files/download?path=documents/file.pdf
     */
    @GetMapping("/download")
    public ResponseEntity<byte[]> download(@RequestParam("path") String path) {
        try {
            byte[] data = blobService.downloadFile(path);
            String fileName = path.substring(path.lastIndexOf('/') + 1);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .body(data);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

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

    /**
     * List folders
     * GET /api/files/folders?folder=documents
     */
    @GetMapping("/folders")
    public ResponseEntity<List<String>> listFolders(
            @RequestParam(value = "folder", defaultValue = "") String folder) {
        List<String> folders = blobService.listFolders(folder);
        return ResponseEntity.ok(folders);
    }
}
