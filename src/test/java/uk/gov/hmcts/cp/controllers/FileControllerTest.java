package uk.gov.hmcts.cp.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import uk.gov.hmcts.cp.openapi.model.FileInfo;
import uk.gov.hmcts.cp.services.AzureBlobService;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    public static final String VND_COURTLISTPUBLISHING_SERVICE_FILES_JSON = "application/vnd.courtlistpublishing-service.files+json";
    private MockMvc mockMvc;

    @Mock
    private AzureBlobService blobService;

    @InjectMocks
    private FileController fileController;

    private static final String BASE_URL = "/api/files";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(fileController).build();
    }

    @Test
    void listFiles_shouldReturnEmptyList_whenNoFilesExist() throws Exception {
        // Given
        String folder = "documents";
        when(blobService.listFiles(folder)).thenReturn(Collections.emptyList());

        // When & Then
        mockMvc.perform(get(BASE_URL + "/list")
                        .param("folder", folder))
                .andExpect(status().isOk())
                .andExpect(content().contentType(VND_COURTLISTPUBLISHING_SERVICE_FILES_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        verify(blobService).listFiles(folder);
    }

    @Test
    void listFiles_shouldReturnListOfFiles_whenFilesExist() throws Exception {
        // Given
        String folder = "documents";
        List<FileInfo> mockFiles = Arrays.asList(
                new FileInfo("file1.pdf", "documents/file1.pdf", URI.create("https://storage.example.com/file1.pdf"), 1024L),
                new FileInfo("file2.docx", "documents/file2.docx", URI.create("https://storage.example.com/file2.docx"), 2048L)
        );

        when(blobService.listFiles(folder)).thenReturn(mockFiles);

        // When & Then
        mockMvc.perform(get(BASE_URL + "/list")
                        .param("folder", folder))
                .andExpect(status().isOk())
                .andExpect(content().contentType(VND_COURTLISTPUBLISHING_SERVICE_FILES_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("file1.pdf"))
                .andExpect(jsonPath("$[0].path").value("documents/file1.pdf"))
                .andExpect(jsonPath("$[0].url").value("https://storage.example.com/file1.pdf"))
                .andExpect(jsonPath("$[0].size").value(1024))
                .andExpect(jsonPath("$[1].name").value("file2.docx"))
                .andExpect(jsonPath("$[1].path").value("documents/file2.docx"))
                .andExpect(jsonPath("$[1].url").value("https://storage.example.com/file2.docx"))
                .andExpect(jsonPath("$[1].size").value(2048));

        verify(blobService).listFiles(folder);
    }

    @Test
    void listFiles_shouldUseDefaultEmptyFolder_whenFolderParameterNotProvided() throws Exception {
        // Given
        List<FileInfo> mockFiles = List.of(
                new FileInfo("rootfile.txt", "rootfile.txt", URI.create("https://storage.example.com/rootfile.txt"), 512L)
        );

        when(blobService.listFiles("")).thenReturn(mockFiles);

        // When & Then
        mockMvc.perform(get(BASE_URL + "/list"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(VND_COURTLISTPUBLISHING_SERVICE_FILES_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("rootfile.txt"));

        verify(blobService).listFiles("");
    }

    @Test
    void listFiles_shouldHandleEmptyFolderParameter() throws Exception {
        // Given
        List<FileInfo> mockFiles = List.of(
                new FileInfo("file.txt", "file.txt", URI.create("https://storage.example.com/file.txt"), 256L)
        );

        when(blobService.listFiles("")).thenReturn(mockFiles);

        // When & Then
        mockMvc.perform(get(BASE_URL + "/list")
                        .param("folder", ""))
                .andExpect(status().isOk())
                .andExpect(content().contentType(VND_COURTLISTPUBLISHING_SERVICE_FILES_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));

        verify(blobService).listFiles("");
    }

    @Test
    void listFiles_shouldHandleNestedFolderPath() throws Exception {
        // Given
        String folder = "documents/2024/january";
        List<FileInfo> mockFiles = List.of(
                new FileInfo("report.pdf", "documents/2024/january/report.pdf", URI.create("https://storage.example.com/report.pdf"), 4096L)
        );

        when(blobService.listFiles(folder)).thenReturn(mockFiles);

        // When & Then
        mockMvc.perform(get(BASE_URL + "/list")
                        .param("folder", folder))
                .andExpect(status().isOk())
                .andExpect(content().contentType(VND_COURTLISTPUBLISHING_SERVICE_FILES_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("report.pdf"))
                .andExpect(jsonPath("$[0].path").value("documents/2024/january/report.pdf"));

        verify(blobService).listFiles(folder);
    }
}
