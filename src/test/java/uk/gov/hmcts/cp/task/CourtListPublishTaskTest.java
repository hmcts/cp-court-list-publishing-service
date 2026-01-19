package uk.gov.hmcts.cp.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.openapi.model.PublishStatus;
import uk.gov.hmcts.cp.repositories.CourtListPublishStatusRepository;
import uk.gov.hmcts.cp.services.CaTHService;
import uk.gov.hmcts.cp.services.CourtListQueryService;
import uk.gov.hmcts.cp.models.transformed.CourtListDocument;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class CourtListPublishTaskTest {

    @Mock
    private CourtListPublishStatusRepository repository;

    @Mock
    private CourtListQueryService courtListQueryService;

    @Mock
    private CaTHService cathService;

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
                PublishStatus.COURT_LIST_REQUESTED,
                CourtListType.PUBLIC,
                Instant.now()
        );
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
        assertThat(entity.getPublishStatus()).isEqualTo(PublishStatus.EXPORT_SUCCESSFUL);
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
        verify(repository, never()).getByCourtListId(any());
        verify(repository, never()).save(any());
    }

    @Test
    void execute_shouldQueryCourtListAndSendToCaTH_whenValidJobDataProvided() {
        // Given
        JsonObject jobData = createJobDataWithCourtListId(courtListId);
        when(executionInfo.getJobData()).thenReturn(jobData);
        when(repository.getByCourtListId(courtListId)).thenReturn(entity);

        String todayDate = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        CourtListDocument courtListDocument = CourtListDocument.builder().build();
        
        when(courtListQueryService.queryCourtList(
                "PUBLIC",
                courtCentreId.toString(),
                todayDate,
                todayDate,
                "7aee5dea-b0de-4604-b49b-86c7788cfc4b"
        )).thenReturn(courtListDocument);

        // When
        ExecutionInfo result = task.execute(executionInfo);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getExecutionStatus()).isEqualTo(COMPLETED);
        verify(courtListQueryService).queryCourtList(
                "PUBLIC",
                courtCentreId.toString(),
                todayDate,
                todayDate,
                "7aee5dea-b0de-4604-b49b-86c7788cfc4b"
        );
        verify(cathService).sendCourtListToCaTH(courtListDocument);
        verify(repository).getByCourtListId(courtListId);
        verify(repository).save(entity);
    }

    @Test
    void execute_shouldNotQueryCourtList_whenJobDataIsNull() {
        // Given
        when(executionInfo.getJobData()).thenReturn(null);

        // When
        ExecutionInfo result = task.execute(executionInfo);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getExecutionStatus()).isEqualTo(COMPLETED);
        verify(courtListQueryService, never()).queryCourtList(any(), any(), any(), any(), any());
        verify(cathService, never()).sendCourtListToCaTH(any());
    }

    @Test
    void execute_shouldNotQueryCourtList_whenListIdIsMissing() {
        // Given
        JsonObject jobData = Json.createObjectBuilder()
                .add("courtListId", courtListId.toString())
                .add("courtCentreId", courtCentreId.toString())
                .build();
        when(executionInfo.getJobData()).thenReturn(jobData);

        // When
        ExecutionInfo result = task.execute(executionInfo);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getExecutionStatus()).isEqualTo(COMPLETED);
        verify(courtListQueryService, never()).queryCourtList(any(), any(), any(), any(), any());
        verify(cathService, never()).sendCourtListToCaTH(any());
    }

    @Test
    void execute_shouldNotQueryCourtList_whenCourtCentreIdIsMissing() {
        // Given
        JsonObject jobData = Json.createObjectBuilder()
                .add("courtListId", courtListId.toString())
                .add("courtListType", "PUBLIC")
                .build();
        when(executionInfo.getJobData()).thenReturn(jobData);

        // When
        ExecutionInfo result = task.execute(executionInfo);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getExecutionStatus()).isEqualTo(COMPLETED);
        verify(courtListQueryService, never()).queryCourtList(any(), any(), any(), any(), any());
        verify(cathService, never()).sendCourtListToCaTH(any());
    }

    @Test
    void execute_shouldHandleException_whenCourtListQueryServiceThrowsException() {
        // Given
        JsonObject jobData = createJobDataWithCourtListId(courtListId);
        when(executionInfo.getJobData()).thenReturn(jobData);
        when(repository.getByCourtListId(courtListId)).thenReturn(entity);
        
        when(courtListQueryService.queryCourtList(
                any(), any(), any(), any(), any()
        )).thenThrow(new RuntimeException("Query service error"));

        // When
        ExecutionInfo result = task.execute(executionInfo);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getExecutionStatus()).isEqualTo(COMPLETED);
        verify(courtListQueryService).queryCourtList(any(), any(), any(), any(), any());
        verify(cathService, never()).sendCourtListToCaTH(any());
        // Status update should still be attempted
        verify(repository).getByCourtListId(courtListId);
    }

    @Test
    void execute_shouldHandleException_whenCaTHServiceThrowsException() {
        // Given
        JsonObject jobData = createJobDataWithCourtListId(courtListId);
        when(executionInfo.getJobData()).thenReturn(jobData);
        when(repository.getByCourtListId(courtListId)).thenReturn(entity);

        CourtListDocument courtListDocument = CourtListDocument.builder().build();
        
        when(courtListQueryService.queryCourtList(
                any(), any(), any(), any(), any()
        )).thenReturn(courtListDocument);
        
        doThrow(new RuntimeException("CaTH service error"))
                .when(cathService).sendCourtListToCaTH(any());

        // When
        ExecutionInfo result = task.execute(executionInfo);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getExecutionStatus()).isEqualTo(COMPLETED);
        verify(courtListQueryService).queryCourtList(any(), any(), any(), any(), any());
        verify(cathService).sendCourtListToCaTH(courtListDocument);
        // Status update should still be attempted
        verify(repository).getByCourtListId(courtListId);
    }

    @Test
    void execute_shouldUseTodayDateForStartDateAndEndDate() {
        // Given
        JsonObject jobData = createJobDataWithCourtListId(courtListId);
        when(executionInfo.getJobData()).thenReturn(jobData);
        when(repository.getByCourtListId(courtListId)).thenReturn(entity);

        String expectedDate = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        CourtListDocument courtListDocument = CourtListDocument.builder().build();
        
        when(courtListQueryService.queryCourtList(
                "PUBLIC",
                courtCentreId.toString(),
                expectedDate,
                expectedDate,
                "7aee5dea-b0de-4604-b49b-86c7788cfc4b"
        )).thenReturn(courtListDocument);

        // When
        task.execute(executionInfo);

        // Then
        verify(courtListQueryService).queryCourtList(
                "PUBLIC",
                courtCentreId.toString(),
                expectedDate,
                expectedDate,
                "7aee5dea-b0de-4604-b49b-86c7788cfc4b"
        );
    }

    private JsonObject createJobDataWithCourtListId(UUID courtListId) {
        return Json.createObjectBuilder()
                .add("courtListId", courtListId.toString())
                .add("courtCentreId", courtCentreId.toString())
                .add("courtListType", "PUBLIC")
                .build();
    }
}
