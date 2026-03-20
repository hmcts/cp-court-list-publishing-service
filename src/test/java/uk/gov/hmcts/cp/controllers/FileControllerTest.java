package uk.gov.hmcts.cp.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import uk.gov.hmcts.cp.services.CourtListPublisherBlobClientService;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    public static final String ACCEPT_FILES_DOWNLOAD = "application/vnd.courtlistpublishing-service.files.download+json";
    private MockMvc mockMvc;

    @Mock
    private CourtListPublisherBlobClientService courtListPublisherBlobService;

    @InjectMocks
    private FileController fileController;

    private static final String BASE_URL = "/api/files";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(fileController).build();
    }

    @Test
    void downloadFile_shouldReturn200_withPdfContent_whenFileExists() throws Exception {
        // Given
        UUID fileId = UUID.fromString("3be64c15-0988-41c8-8345-6fef4218f5eb");
        byte[] pdfContent = "mock pdf content".getBytes();
        when(courtListPublisherBlobService.downloadPdf(fileId)).thenReturn(Optional.of(pdfContent));

        // When & Then
        mockMvc.perform(get(BASE_URL + "/download/{fileId}", fileId)
                        .header("Accept", ACCEPT_FILES_DOWNLOAD))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"" + fileId + ".pdf\""))
                .andExpect(content().bytes(pdfContent));

        verify(courtListPublisherBlobService).downloadPdf(fileId);
    }

    @Test
    void downloadFile_shouldReturn404_whenFileDoesNotExist() throws Exception {
        // Given
        UUID fileId = UUID.randomUUID();
        when(courtListPublisherBlobService.downloadPdf(fileId)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get(BASE_URL + "/download/{fileId}", fileId)
                        .header("Accept", ACCEPT_FILES_DOWNLOAD))
                .andExpect(status().isNotFound());

        verify(courtListPublisherBlobService).downloadPdf(fileId);
    }

    @Test
    void downloadFile_shouldSetCorrectContentDispositionHeader() throws Exception {
        // Given
        UUID fileId = UUID.fromString("123e4567-e89b-12d3-a456-426614174002");
        byte[] pdfContent = "pdf".getBytes();
        when(courtListPublisherBlobService.downloadPdf(fileId)).thenReturn(Optional.of(pdfContent));

        // When & Then
        mockMvc.perform(get(BASE_URL + "/download/{fileId}", fileId)
                        .header("Accept", ACCEPT_FILES_DOWNLOAD))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"123e4567-e89b-12d3-a456-426614174002.pdf\""));

        verify(courtListPublisherBlobService).downloadPdf(fileId);
    }
}
