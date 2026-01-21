package uk.gov.hmcts.cp.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus.COMPLETED;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.domain.CourtListPublishStatusEntity;
import uk.gov.hmcts.cp.repositories.CourtListPublishStatusRepository;
import uk.gov.hmcts.cp.services.PdfGenerationService;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;

import java.io.IOException;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class PdfGenerationTaskTest {

    @Mock
    private PdfGenerationService pdfGenerationService;

    @Mock
    private CourtListPublishStatusRepository repository;

    @Mock
    private ExecutionInfo executionInfo;

    @InjectMocks
    private PdfGenerationTask task;

    private UUID courtListId;
    private CourtListPublishStatusEntity entity;
    private JsonObject payload;

    @BeforeEach
    void setUp() {
        courtListId = UUID.randomUUID();
        UUID courtCentreId = UUID.randomUUID();
        entity = new CourtListPublishStatusEntity(
                courtListId,
                courtCentreId,
                null, // courtRoomId
                uk.gov.hmcts.cp.openapi.model.PublishStatus.PUBLISH_REQUESTED,
                uk.gov.hmcts.cp.openapi.model.CourtListType.PUBLIC,
                java.time.Instant.now()
        );
        payload = Json.createObjectBuilder()
                .add("listType", "PUBLIC")
                .add("courtCentreName", "Test Court")
                .build();
    }

    @Test
    void execute_shouldReturnCompletedStatus_whenPdfGenerationSucceeds() throws IOException {
        // Given
        String expectedSasUrl = "https://storage.example.com/blob.pdf?sasToken";
        JsonObject jobData = createJobDataWithPayload(courtListId, payload);
        
        when(executionInfo.getJobData()).thenReturn(jobData);
        when(pdfGenerationService.generateAndUploadPdf(eq(payload), eq(courtListId)))
                .thenReturn(expectedSasUrl);
        when(repository.getByCourtListId(courtListId)).thenReturn(entity);

        // When
        ExecutionInfo result = task.execute(executionInfo);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getExecutionStatus()).isEqualTo(COMPLETED);
        assertThat(result.getJobData()).isNotNull();
        assertThat(result.getJobData().getString("sasUrl")).isEqualTo(expectedSasUrl);
        assertThat(entity.getFileName()).isEqualTo(expectedSasUrl);
        verify(pdfGenerationService).generateAndUploadPdf(eq(payload), eq(courtListId));
        verify(repository).getByCourtListId(courtListId);
        verify(repository).save(entity);
    }

    @Test
    void execute_shouldContinue_whenRepositorySaveFails() throws IOException {
        // Given
        String expectedSasUrl = "https://storage.example.com/blob.pdf?sasToken";
        JsonObject jobData = createJobDataWithPayload(courtListId, payload);
        
        when(executionInfo.getJobData()).thenReturn(jobData);
        when(pdfGenerationService.generateAndUploadPdf(eq(payload), eq(courtListId)))
                .thenReturn(expectedSasUrl);
        when(repository.getByCourtListId(courtListId)).thenReturn(entity);
        when(repository.save(entity)).thenThrow(new RuntimeException("Database error"));

        // When
        ExecutionInfo result = task.execute(executionInfo);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getExecutionStatus()).isEqualTo(COMPLETED);
        assertThat(result.getJobData().getString("sasUrl")).isEqualTo(expectedSasUrl);
        verify(pdfGenerationService).generateAndUploadPdf(eq(payload), eq(courtListId));
        verify(repository).getByCourtListId(courtListId);
        verify(repository).save(entity);
    }

    @Test
    void execute_shouldUpdateJobDataWithSasUrl_whenSuccessful() throws IOException {
        // Given
        String expectedSasUrl = "https://storage.example.com/blob.pdf?sasToken";
        JsonObject jobData = createJobDataWithPayload(courtListId, payload);
        
        when(executionInfo.getJobData()).thenReturn(jobData);
        when(pdfGenerationService.generateAndUploadPdf(eq(payload), eq(courtListId)))
                .thenReturn(expectedSasUrl);
        when(repository.getByCourtListId(courtListId)).thenReturn(entity);

        // When
        ExecutionInfo result = task.execute(executionInfo);

        // Then
        assertThat(result.getJobData()).isNotNull();
        assertThat(result.getJobData().containsKey("sasUrl")).isTrue();
        assertThat(result.getJobData().getString("sasUrl")).isEqualTo(expectedSasUrl);
        assertThat(result.getJobData().containsKey("courtListId")).isTrue();
        assertThat(result.getJobData().containsKey("payload")).isTrue();
    }

    @Test
    void execute_shouldHandleNullSasUrl() throws IOException {
        // Given
        JsonObject jobData = createJobDataWithPayload(courtListId, payload);
        
        when(executionInfo.getJobData()).thenReturn(jobData);
        when(pdfGenerationService.generateAndUploadPdf(eq(payload), eq(courtListId)))
                .thenReturn(null);
        when(repository.getByCourtListId(courtListId)).thenReturn(entity);

        // When
        ExecutionInfo result = task.execute(executionInfo);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getExecutionStatus()).isEqualTo(COMPLETED);
        assertThat(result.getJobData().containsKey("sasUrl")).isFalse();
        verify(repository).getByCourtListId(courtListId);
        verify(repository).save(entity);
        assertThat(entity.getFileName()).isNull();
    }

    private JsonObject createJobDataWithPayload(UUID courtListId, JsonObject payload) {
        return Json.createObjectBuilder()
                .add("courtListId", courtListId.toString())
                .add("payload", payload)
                .build();
    }
}
