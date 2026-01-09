package uk.gov.hmcts.cp.services;

import com.taskmanager.domain.ExecutionInfo;
import com.taskmanager.domain.ExecutionStatus;
import com.taskmanager.service.ExecutionService;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishRequest;
import uk.gov.hmcts.cp.openapi.model.CourtListType;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PublishJobTriggerServiceTest {

    @Mock
    private ExecutionService executionService;

    @InjectMocks
    private PublishJobTriggerService service;

    private CourtListPublishRequest validRequest;
    private UUID courtCentreId;

    @BeforeEach
    void setUp() {
        courtCentreId = UUID.randomUUID();
        validRequest = new CourtListPublishRequest(courtCentreId, CourtListType.STANDARD);
    }

    @Test
    void triggerCourtListPublishingTask_shouldThrowException_whenRequestIsNull() {
        // When & Then
        assertThatThrownBy(() -> service.triggerCourtListPublishingTask(null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException ex = (ResponseStatusException) exception;
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getReason()).isEqualTo("Request is required");
                });

        verifyNoInteractions(executionService);
    }

    @Test
    void triggerCourtListPublishingTask_shouldThrowException_whenCourtCentreIdIsNull() {
        // Given
        CourtListPublishRequest request = new CourtListPublishRequest(null, CourtListType.STANDARD);

        // When & Then
        assertThatThrownBy(() -> service.triggerCourtListPublishingTask(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException ex = (ResponseStatusException) exception;
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getReason()).isEqualTo("Court centre ID is required");
                });

        verifyNoInteractions(executionService);
    }

    @Test
    void triggerCourtListPublishingTask_shouldThrowException_whenCourtListTypeIsNull() {
        // Given
        CourtListPublishRequest request = new CourtListPublishRequest(courtCentreId, null);

        // When & Then
        assertThatThrownBy(() -> service.triggerCourtListPublishingTask(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException ex = (ResponseStatusException) exception;
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getReason()).isEqualTo("Court list type is required");
                });

        verifyNoInteractions(executionService);
    }

    @Test
    void triggerCourtListPublishingTask_shouldCallExecutionService_whenValidRequestProvided() {
        // Given
        doNothing().when(executionService).executeWith(any(ExecutionInfo.class));

        // When
        service.triggerCourtListPublishingTask(validRequest);

        // Then
        ArgumentCaptor<ExecutionInfo> executionInfoCaptor = ArgumentCaptor.forClass(ExecutionInfo.class);
        verify(executionService).executeWith(executionInfoCaptor.capture());

        ExecutionInfo capturedExecutionInfo = executionInfoCaptor.getValue();
        assertThat(capturedExecutionInfo).isNotNull();
        assertThat(capturedExecutionInfo.getAssignedTaskName()).isEqualTo("COURT_LIST_PUBLISH_TASK");
        assertThat(capturedExecutionInfo.getExecutionStatus()).isEqualTo(ExecutionStatus.STARTED);
        assertThat(capturedExecutionInfo.getAssignedTaskStartTime()).isNotNull();
    }

    @Test
    void triggerCourtListPublishingTask_shouldCreateCorrectJobData_whenValidRequestProvided() {
        // Given
        doNothing().when(executionService).executeWith(any(ExecutionInfo.class));

        // When
        service.triggerCourtListPublishingTask(validRequest);

        // Then
        ArgumentCaptor<ExecutionInfo> executionInfoCaptor = ArgumentCaptor.forClass(ExecutionInfo.class);
        verify(executionService).executeWith(executionInfoCaptor.capture());

        ExecutionInfo capturedExecutionInfo = executionInfoCaptor.getValue();
        JsonObject jobData = capturedExecutionInfo.getJobData();

        assertThat(jobData).isNotNull();
        assertThat(jobData.containsKey("payload")).isTrue();

        JsonObject payload = jobData.getJsonObject("payload");
        assertThat(payload).isNotNull();
        assertThat(payload.getString("courtCentreId")).isEqualTo(courtCentreId.toString());
        assertThat(payload.getString("courtListType")).isEqualTo(CourtListType.STANDARD.toString());
    }

    @Test
    void triggerCourtListPublishingTask_shouldHandleDifferentCourtListTypes() {
        // Given
        doNothing().when(executionService).executeWith(any(ExecutionInfo.class));
        
        // Test with different court list types
        CourtListType[] types = {CourtListType.STANDARD, CourtListType.PUBLIC, CourtListType.FINAL};
        ArgumentCaptor<ExecutionInfo> executionInfoCaptor = ArgumentCaptor.forClass(ExecutionInfo.class);

        for (CourtListType type : types) {
            CourtListPublishRequest request = new CourtListPublishRequest(courtCentreId, type);

            // When
            service.triggerCourtListPublishingTask(request);
        }

        // Then - verify all calls and check each one
        verify(executionService, org.mockito.Mockito.times(types.length)).executeWith(executionInfoCaptor.capture());
        
        var allCapturedExecutionInfo = executionInfoCaptor.getAllValues();
        assertThat(allCapturedExecutionInfo).hasSize(types.length);
        
        for (int i = 0; i < types.length; i++) {
            ExecutionInfo capturedExecutionInfo = allCapturedExecutionInfo.get(i);
            JsonObject jobData = capturedExecutionInfo.getJobData();
            JsonObject payload = jobData.getJsonObject("payload");
            assertThat(payload.getString("courtListType")).isEqualTo(types[i].toString());
        }
    }

    @Test
    void triggerCourtListPublishingTask_shouldSetCorrectTaskStartTime() {
        // Given
        doNothing().when(executionService).executeWith(any(ExecutionInfo.class));
        ZonedDateTime beforeExecution = ZonedDateTime.now();

        // When
        service.triggerCourtListPublishingTask(validRequest);

        // Then
        ArgumentCaptor<ExecutionInfo> executionInfoCaptor = ArgumentCaptor.forClass(ExecutionInfo.class);
        verify(executionService).executeWith(executionInfoCaptor.capture());

        ExecutionInfo capturedExecutionInfo = executionInfoCaptor.getValue();
        ZonedDateTime taskStartTime = capturedExecutionInfo.getAssignedTaskStartTime();
        ZonedDateTime afterExecution = ZonedDateTime.now();

        assertThat(taskStartTime).isNotNull();
        assertThat(taskStartTime).isAfterOrEqualTo(beforeExecution);
        assertThat(taskStartTime).isBeforeOrEqualTo(afterExecution);
    }

    @Test
    void triggerCourtListPublishingTask_shouldSetExecutionStatusToStarted() {
        // Given
        doNothing().when(executionService).executeWith(any(ExecutionInfo.class));

        // When
        service.triggerCourtListPublishingTask(validRequest);

        // Then
        ArgumentCaptor<ExecutionInfo> executionInfoCaptor = ArgumentCaptor.forClass(ExecutionInfo.class);
        verify(executionService).executeWith(executionInfoCaptor.capture());

        ExecutionInfo capturedExecutionInfo = executionInfoCaptor.getValue();
        assertThat(capturedExecutionInfo.getExecutionStatus()).isEqualTo(ExecutionStatus.STARTED);
    }

}

