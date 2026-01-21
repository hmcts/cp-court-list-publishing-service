package uk.gov.hmcts.cp.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus.COMPLETED;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.domain.CourtListPublishStatusEntity;
import uk.gov.hmcts.cp.models.transformed.CourtListDocument;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.openapi.model.PublishStatus;
import uk.gov.hmcts.cp.repositories.CourtListPublishStatusRepository;
import uk.gov.hmcts.cp.services.CaTHService;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;

import java.time.Instant;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class CourtListPublishTaskTest {

    @Mock
    private CourtListPublishStatusRepository repository;

    @Mock
    private CaTHService cathService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ExecutionInfo executionInfo;

    @InjectMocks
    private CourtListPublishTask task;

    private UUID courtListId;
    private UUID courtCentreId;
    private CourtListPublishStatusEntity entity;

    @BeforeEach
    void setUp() {
        courtListId = UUID.randomUUID();
        courtCentreId = UUID.randomUUID();
        entity = new CourtListPublishStatusEntity(
                courtListId,
                courtCentreId,
                null, // courtRoomId
                PublishStatus.PUBLISH_REQUESTED,
                CourtListType.PUBLIC,
                Instant.now()
        );
    }

    @Test
    void execute_shouldExtractCourtListDocumentAndSendToCaTH_whenValidJobDataProvided() throws Exception {
        // Given
        CourtListDocument courtListDocument = CourtListDocument.builder().build();
        JsonObject jobData = createJobDataWithPayload(courtListId, courtListDocument);
        when(executionInfo.getJobData()).thenReturn(jobData);
        when(repository.getByCourtListId(courtListId)).thenReturn(entity);
        when(objectMapper.readValue(anyString(), eq(CourtListDocument.class))).thenReturn(courtListDocument);

        // When
        ExecutionInfo result = task.execute(executionInfo);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getExecutionStatus()).isEqualTo(COMPLETED);
        assertThat(entity.getPublishStatus()).isEqualTo(PublishStatus.PUBLISH_SUCCESSFUL);
        assertThat(entity.getLastUpdated()).isNotNull();
        verify(objectMapper).readValue(anyString(), eq(CourtListDocument.class));
        verify(cathService).sendCourtListToCaTH(courtListDocument);
        verify(repository).getByCourtListId(courtListId);
        verify(repository).save(entity);
    }

    @Test
    void execute_shouldUpdateStatusToExportSuccessful_whenValidCourtListIdProvided() {
        // Given
        JsonObject jobData = createJobDataWithCourtListId(courtListId);
        when(executionInfo.getJobData()).thenReturn(jobData);
        when(repository.getByCourtListId(courtListId)).thenReturn(entity);

        // When
        ExecutionInfo result = task.execute(executionInfo);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getExecutionStatus()).isEqualTo(COMPLETED);
        assertThat(entity.getPublishStatus()).isEqualTo(PublishStatus.PUBLISH_SUCCESSFUL);
        assertThat(entity.getLastUpdated()).isNotNull();
        verify(repository).getByCourtListId(courtListId);
        verify(repository).save(entity);
    }

    @Test
    void execute_shouldReturnCompletedStatus_whenJobDataIsNull() {
        // Given
        when(executionInfo.getJobData()).thenReturn(null);

        // When
        ExecutionInfo result = task.execute(executionInfo);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getExecutionStatus()).isEqualTo(COMPLETED);
        verify(cathService, never()).sendCourtListToCaTH(any());
        verify(repository, never()).getByCourtListId(any());
        verify(repository, never()).save(any());
    }

    @Test
    void execute_shouldReturnCompletedStatus_whenCourtListIdIsMissingInJobData() {
        // Given
        JsonObject jobData = Json.createObjectBuilder()
                .add("courtCentreId", courtCentreId.toString())
                .add("courtListType", "PUBLIC")
                .build();
        when(executionInfo.getJobData()).thenReturn(jobData);

        // When
        ExecutionInfo result = task.execute(executionInfo);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getExecutionStatus()).isEqualTo(COMPLETED);
        verify(repository, never()).getByCourtListId(any());
        verify(repository, never()).save(any());
    }

    @Test
    void execute_shouldReturnCompletedStatus_whenCourtListIdIsNullInJobData() {
        // Given
        // JsonObjectBuilder doesn't allow null values, so we mock JsonObject to return null
        JsonObject mockJobData = org.mockito.Mockito.mock(JsonObject.class);
        when(mockJobData.getString("courtListId", null)).thenReturn(null);
        when(executionInfo.getJobData()).thenReturn(mockJobData);

        // When
        ExecutionInfo result = task.execute(executionInfo);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getExecutionStatus()).isEqualTo(COMPLETED);
        verify(repository, never()).getByCourtListId(any());
        verify(repository, never()).save(any());
    }

    @Test
    void execute_shouldReturnCompletedStatus_whenCourtListIdHasInvalidUuidFormat() {
        // Given
        JsonObject jobData = Json.createObjectBuilder()
                .add("courtListId", "invalid-uuid-format")
                .build();
        when(executionInfo.getJobData()).thenReturn(jobData);

        // When
        ExecutionInfo result = task.execute(executionInfo);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getExecutionStatus()).isEqualTo(COMPLETED);
        verify(repository, never()).getByCourtListId(any());
        verify(repository, never()).save(any());
    }

    @Test
    void execute_shouldReturnCompletedStatus_whenEntityNotFound() {
        // Given
        JsonObject jobData = createJobDataWithCourtListId(courtListId);
        when(executionInfo.getJobData()).thenReturn(jobData);
        when(repository.getByCourtListId(courtListId)).thenReturn(null);

        // When
        ExecutionInfo result = task.execute(executionInfo);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getExecutionStatus()).isEqualTo(COMPLETED);
        verify(repository).getByCourtListId(courtListId);
        verify(repository, never()).save(any());
    }

    @Test
    void execute_shouldReturnCompletedStatus_whenRepositoryThrowsException() {
        // Given
        JsonObject jobData = createJobDataWithCourtListId(courtListId);
        when(executionInfo.getJobData()).thenReturn(jobData);
        when(repository.getByCourtListId(courtListId)).thenThrow(new RuntimeException("Database error"));

        // When
        ExecutionInfo result = task.execute(executionInfo);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getExecutionStatus()).isEqualTo(COMPLETED);
        verify(repository).getByCourtListId(courtListId);
        verify(repository, never()).save(any());
    }

    @Test
    void execute_shouldReturnCompletedStatus_whenSaveThrowsException() {
        // Given
        JsonObject jobData = createJobDataWithCourtListId(courtListId);
        when(executionInfo.getJobData()).thenReturn(jobData);
        when(repository.getByCourtListId(courtListId)).thenReturn(entity);
        when(repository.save(entity)).thenThrow(new RuntimeException("Save error"));

        // When
        ExecutionInfo result = task.execute(executionInfo);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getExecutionStatus()).isEqualTo(COMPLETED);
        verify(repository).getByCourtListId(courtListId);
        verify(repository).save(entity);
    }

    @Test
    void execute_shouldUpdateLastUpdatedTimestamp_whenStatusIsUpdated() {
        // Given
        Instant originalLastUpdated = Instant.now().minusSeconds(100);
        entity.setLastUpdated(originalLastUpdated);
        
        JsonObject jobData = createJobDataWithCourtListId(courtListId);
        when(executionInfo.getJobData()).thenReturn(jobData);
        when(repository.getByCourtListId(courtListId)).thenReturn(entity);

        // When
        task.execute(executionInfo);

        // Then
        assertThat(entity.getLastUpdated()).isAfter(originalLastUpdated);
        verify(repository).save(entity);
    }

    @Test
    void execute_shouldHandleEmptyJobData() {
        // Given
        JsonObject jobData = Json.createObjectBuilder().build();
        when(executionInfo.getJobData()).thenReturn(jobData);

        // When
        ExecutionInfo result = task.execute(executionInfo);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getExecutionStatus()).isEqualTo(COMPLETED);
        verify(cathService, never()).sendCourtListToCaTH(any());
        verify(repository, never()).getByCourtListId(any());
        verify(repository, never()).save(any());
    }

    @Test
    void execute_shouldReturnCompletedStatus_whenPayloadIsMissing() {
        // Given
        JsonObject jobData = Json.createObjectBuilder()
                .add("courtListId", courtListId.toString())
                .build();
        when(executionInfo.getJobData()).thenReturn(jobData);
        when(repository.getByCourtListId(courtListId)).thenReturn(entity);

        // When
        ExecutionInfo result = task.execute(executionInfo);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getExecutionStatus()).isEqualTo(COMPLETED);
        verify(cathService, never()).sendCourtListToCaTH(any());
        verify(repository).getByCourtListId(courtListId);
        verify(repository).save(entity);
    }

    @Test
    void execute_shouldNotSendToCaTH_whenExtractingCourtListDocumentFails() throws Exception {
        // Given
        CourtListDocument courtListDocument = CourtListDocument.builder().build();
        JsonObject jobData = createJobDataWithPayload(courtListId, courtListDocument);
        when(executionInfo.getJobData()).thenReturn(jobData);
        when(repository.getByCourtListId(courtListId)).thenReturn(entity);
        when(objectMapper.readValue(anyString(), eq(CourtListDocument.class)))
                .thenThrow(new JsonProcessingException("JSON error") {});

        // When
        ExecutionInfo result = task.execute(executionInfo);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getExecutionStatus()).isEqualTo(COMPLETED);
        verify(objectMapper).readValue(anyString(), eq(CourtListDocument.class));
        verify(cathService, never()).sendCourtListToCaTH(any());
        verify(repository).getByCourtListId(courtListId);
        verify(repository).save(entity);
    }

    @Test
    void execute_shouldStillUpdateStatus_whenCaTHServiceThrowsException() throws Exception {
        // Given
        CourtListDocument courtListDocument = CourtListDocument.builder().build();
        JsonObject jobData = createJobDataWithPayload(courtListId, courtListDocument);
        when(executionInfo.getJobData()).thenReturn(jobData);
        when(repository.getByCourtListId(courtListId)).thenReturn(entity);
        when(objectMapper.readValue(anyString(), eq(CourtListDocument.class))).thenReturn(courtListDocument);
        org.mockito.Mockito.doThrow(new RuntimeException("CaTH service error"))
                .when(cathService).sendCourtListToCaTH(any());

        // When
        ExecutionInfo result = task.execute(executionInfo);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getExecutionStatus()).isEqualTo(COMPLETED);
        verify(objectMapper).readValue(anyString(), eq(CourtListDocument.class));
        verify(cathService).sendCourtListToCaTH(courtListDocument);
        verify(repository).getByCourtListId(courtListId);
        verify(repository).save(entity);
    }

    private JsonObject createJobDataWithCourtListId(UUID courtListId) {
        return Json.createObjectBuilder()
                .add("courtListId", courtListId.toString())
                .add("courtCentreId", courtCentreId.toString())
                .add("courtListType", "PUBLIC")
                .build();
    }

    private JsonObject createJobDataWithPayload(UUID courtListId, CourtListDocument document) throws JsonProcessingException {
        String documentJson = new ObjectMapper().writeValueAsString(document);
        JsonObject payload = Json.createReader(new java.io.StringReader(documentJson)).readObject();
        return Json.createObjectBuilder()
                .add("courtListId", courtListId.toString())
                .add("payload", payload)
                .build();
    }
}
