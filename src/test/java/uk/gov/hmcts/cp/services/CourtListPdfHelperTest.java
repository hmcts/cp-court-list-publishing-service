package uk.gov.hmcts.cp.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.models.CourtListPayload;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.taskmanager.domain.converter.JsonObjectConverter;

import java.io.IOException;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class CourtListPdfHelperTest {

    @Mock
    private PdfGenerationService pdfGenerationService;

    @Mock
    private JsonObjectConverter objectConverter;

    @InjectMocks
    private CourtListPdfHelper pdfHelper;

    private UUID courtListId;
    private CourtListPayload payload;

    @BeforeEach
    void setUp() {
        courtListId = UUID.randomUUID();
        payload = new CourtListPayload();
    }

    @Test
    void generateAndUploadPdf_shouldReturnFileId_whenSuccessful() throws IOException {
        jakarta.json.JsonObject payloadJson = jakarta.json.Json.createObjectBuilder().build();
        when(objectConverter.convertFromObject(payload)).thenReturn(payloadJson);
        when(pdfGenerationService.generateAndUploadPdf(any(), eq(courtListId), eq(CourtListType.STANDARD), eq(Boolean.TRUE.equals(payload.getIsWelsh())))).thenReturn(courtListId);

        UUID result = pdfHelper.generateAndUploadPdf(payload, courtListId, CourtListType.STANDARD);

        assertThat(result).isEqualTo(courtListId);
        verify(pdfGenerationService).generateAndUploadPdf(any(), eq(courtListId), eq(CourtListType.STANDARD), eq(Boolean.TRUE.equals(payload.getIsWelsh())));
    }

    @Test
    void generateAndUploadPdf_shouldPassFalseForNonWelsh_whenPayloadIsWelshFalse() throws IOException {
        payload.setIsWelsh(false);
        jakarta.json.JsonObject payloadJson = jakarta.json.Json.createObjectBuilder().build();
        when(objectConverter.convertFromObject(payload)).thenReturn(payloadJson);
        when(pdfGenerationService.generateAndUploadPdf(any(), eq(courtListId), eq(CourtListType.STANDARD), eq(false))).thenReturn(courtListId);

        UUID result = pdfHelper.generateAndUploadPdf(payload, courtListId, CourtListType.STANDARD);

        assertThat(result).isEqualTo(courtListId);
        verify(pdfGenerationService).generateAndUploadPdf(any(), eq(courtListId), eq(CourtListType.STANDARD), eq(false));
    }

    @Test
    void generateAndUploadPdf_shouldReturnNull_whenPayloadIsNull() throws IOException {
        UUID result = pdfHelper.generateAndUploadPdf(null, courtListId, CourtListType.STANDARD);

        assertThat(result).isNull();
        verify(pdfGenerationService, never()).generateAndUploadPdf(any(), any(), any(), anyBoolean());
    }

    @Test
    void generateAndUploadPdf_shouldThrowRuntimeException_whenPdfGenerationThrowsException() throws IOException {
        jakarta.json.JsonObject payloadJson = jakarta.json.Json.createObjectBuilder().build();
        when(objectConverter.convertFromObject(payload)).thenReturn(payloadJson);
        when(pdfGenerationService.generateAndUploadPdf(any(), eq(courtListId), eq(CourtListType.STANDARD), eq(Boolean.TRUE.equals(payload.getIsWelsh()))))
                .thenThrow(new IOException("PDF generation failed"));

        assertThatThrownBy(() -> pdfHelper.generateAndUploadPdf(payload, courtListId, CourtListType.STANDARD))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to generate or upload PDF")
                .hasMessageContaining("PDF generation failed");
        verify(pdfGenerationService).generateAndUploadPdf(any(), eq(courtListId), eq(CourtListType.STANDARD), eq(Boolean.TRUE.equals(payload.getIsWelsh())));
    }
}
