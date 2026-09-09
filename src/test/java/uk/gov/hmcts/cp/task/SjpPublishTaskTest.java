package uk.gov.hmcts.cp.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus.COMPLETED;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;
import uk.gov.hmcts.cp.domain.CourtListStatusEntity;
import uk.gov.hmcts.cp.domain.DtsMeta;
import uk.gov.hmcts.cp.domain.sjp.SjpListPayload;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.openapi.model.SjpListType;
import uk.gov.hmcts.cp.openapi.model.Status;
import uk.gov.hmcts.cp.repositories.CourtListStatusRepository;
import uk.gov.hmcts.cp.services.AzureBlobService;
import uk.gov.hmcts.cp.services.CaTHService;
import uk.gov.hmcts.cp.services.CourtListPublisher;
import uk.gov.hmcts.cp.services.CourtListStatusUpdater;
import uk.gov.hmcts.cp.services.JsonSchemaValidatorService;
import uk.gov.hmcts.cp.services.sanitization.DocumentSanitizer;
import uk.gov.hmcts.cp.services.sanitization.HtmlStrippingSanitizer;
import uk.gov.hmcts.cp.services.sanitization.RequiredStringFieldsRegistry;
import uk.gov.hmcts.cp.services.sanitization.WafPatternSanitizer;
import uk.gov.hmcts.cp.services.sjp.SjpToCathPayloadTransformer;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class SjpPublishTaskTest {

    @Mock
    private CourtListStatusRepository repository;

    @Mock
    private CourtListPublisher courtListPublisher;

    @Mock
    private JsonSchemaValidatorService jsonSchemaValidatorService;

    @Mock
    private AzureBlobService azureBlobService;

    @Mock
    private ExecutionInfo executionInfo;

    private SjpPublishTask task;

    private static final List<Map<String, Object>> ONE_CASE = List.of(Map.of(
            "caseUrn", "URN1",
            "defendantName", "D",
            "prosecutorName", "P",
            "sjpOffences", List.of(Map.of("title", "t", "wording", "w"))));

    private static final DocumentSanitizer SANITIZER = new DocumentSanitizer(
            new WafPatternSanitizer("..\\.\\,../"),
            new HtmlStrippingSanitizer(),
            new RequiredStringFieldsRegistry());

    private UUID courtListId;

    @BeforeEach
    void setUp() {
        courtListId = UUID.randomUUID();
        task = new SjpPublishTask(
                new CourtListStatusUpdater(repository),
                new SjpToCathPayloadTransformer(),
                courtListPublisher,
                SANITIZER,
                jsonSchemaValidatorService,
                Optional.of(azureBlobService));
    }

    private JsonObject jobData(UUID id, String listType, SjpListPayload payload, String language, String requestType) {
        try {
            String payloadJson = ObjectMapperConfig.getObjectMapper().writeValueAsString(payload);
            var builder = Json.createObjectBuilder()
                    .add(JobDataConstant.SJP_LIST_ID, id.toString())
                    .add(JobDataConstant.SJP_LIST_TYPE, listType)
                    .add(JobDataConstant.SJP_PAYLOAD, payloadJson);
            if (language != null) {
                builder.add(JobDataConstant.SJP_LANGUAGE, language);
            }
            if (requestType != null) {
                builder.add(JobDataConstant.SJP_REQUEST_TYPE, requestType);
            }
            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private DtsMeta capturePublishedMeta() {
        ArgumentCaptor<DtsMeta> captor = ArgumentCaptor.forClass(DtsMeta.class);
        verify(courtListPublisher).publish(anyString(), captor.capture());
        return captor.getValue();
    }

    // ── DtsMeta building (courtId, language, requestType) ───────────────────

    @Test
    void execute_usesCourtIdNumericOnDtsMeta_whenPresent() {
        when(courtListPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(200);
        SjpListPayload payload = new SjpListPayload("2025-03-09T10:00:00", ONE_CASE, "325");
        when(executionInfo.getJobData()).thenReturn(
                jobData(courtListId, SjpListType.SJP_PUBLIC_LIST.getValue(), payload, null, null));

        task.execute(executionInfo);

        assertThat(capturePublishedMeta().getCourtId()).isEqualTo("325");
    }

    @Test
    void execute_fallsBackToZeroOnDtsMeta_whenCourtIdNumericBlank() {
        when(courtListPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(200);
        SjpListPayload payload = new SjpListPayload("2025-03-09T10:00:00", ONE_CASE, "   ");
        when(executionInfo.getJobData()).thenReturn(
                jobData(courtListId, SjpListType.SJP_PUBLIC_LIST.getValue(), payload, null, null));

        task.execute(executionInfo);

        assertThat(capturePublishedMeta().getCourtId()).isEqualTo("0");
    }

    @Test
    void execute_setsLanguageToWelsh_whenPayloadIsWelshTrue() {
        when(courtListPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(200);
        SjpListPayload payload = new SjpListPayload("2025-03-09T10:00:00", ONE_CASE, null, true);
        when(executionInfo.getJobData()).thenReturn(
                jobData(courtListId, SjpListType.SJP_PUBLIC_LIST.getValue(), payload, null, null));

        task.execute(executionInfo);

        assertThat(capturePublishedMeta().getLanguage()).isEqualTo("WELSH");
    }

    @Test
    void execute_explicitLanguageOverridesIsWelsh() {
        when(courtListPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(200);
        SjpListPayload payload = new SjpListPayload("2025-03-09T10:00:00", ONE_CASE, null, true);
        when(executionInfo.getJobData()).thenReturn(
                jobData(courtListId, SjpListType.SJP_PUBLIC_LIST.getValue(), payload, "ENGLISH", null));

        task.execute(executionInfo);

        assertThat(capturePublishedMeta().getLanguage()).isEqualTo("ENGLISH");
    }

    @Test
    void execute_passesRequestTypeToMeta_whenProvided() {
        when(courtListPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(200);
        SjpListPayload payload = new SjpListPayload("2025-03-09T10:00:00", ONE_CASE);
        when(executionInfo.getJobData()).thenReturn(
                jobData(courtListId, SjpListType.SJP_PUBLIC_LIST.getValue(), payload, null, "FULL"));

        task.execute(executionInfo);

        assertThat(capturePublishedMeta().getRequestType()).isEqualTo("FULL");
    }

    @Test
    void execute_mapsPressListType() {
        when(courtListPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(200);
        SjpListPayload payload = new SjpListPayload("2025-03-09T10:00:00", ONE_CASE);
        when(executionInfo.getJobData()).thenReturn(
                jobData(courtListId, SjpListType.SJP_PRESS_LIST.getValue(), payload, null, null));

        task.execute(executionInfo);

        DtsMeta meta = capturePublishedMeta();
        assertThat(meta.getListType()).isEqualTo("SJP_PRESS_LIST");
        assertThat(meta.getSensitivity()).isEqualTo("CLASSIFIED");
    }

    // ── Delta list types: forwarded to CaTH verbatim, not collapsed to full ──

    @Test
    void execute_forwardsDeltaPublicListTypeToCaTH_insteadOfCollapsingToFull() {
        when(courtListPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(200);
        SjpListPayload payload = new SjpListPayload("2025-03-09T10:00:00", ONE_CASE);
        when(executionInfo.getJobData()).thenReturn(
                jobData(courtListId, SjpListType.SJP_DELTA_PUBLIC_LIST.getValue(), payload, null, null));

        task.execute(executionInfo);

        DtsMeta meta = capturePublishedMeta();
        assertThat(meta.getListType()).isEqualTo("SJP_DELTA_PUBLIC_LIST");
        assertThat(meta.getSensitivity()).isEqualTo("PUBLIC");
    }

    @Test
    void execute_forwardsDeltaPressListTypeToCaTH_andTreatsItAsPressForSensitivity() {
        when(courtListPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(200);
        SjpListPayload payload = new SjpListPayload("2025-03-09T10:00:00", ONE_CASE);
        when(executionInfo.getJobData()).thenReturn(
                jobData(courtListId, SjpListType.SJP_DELTA_PRESS_LIST.getValue(), payload, null, null));

        task.execute(executionInfo);

        DtsMeta meta = capturePublishedMeta();
        assertThat(meta.getListType()).isEqualTo("SJP_DELTA_PRESS_LIST");
        assertThat(meta.getSensitivity()).isEqualTo("CLASSIFIED");
    }

    // ── blob upload (unique-uuid, before publish, shared naming with standard flow) ──

    @Test
    void execute_uploadsPayloadToBlobBeforePublishing_withBlobNameFromCaTHService() {
        when(courtListPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(200);
        SjpListPayload payload = new SjpListPayload("2025-03-09T10:00:00", ONE_CASE);
        when(executionInfo.getJobData()).thenReturn(
                jobData(courtListId, SjpListType.SJP_PUBLIC_LIST.getValue(), payload, null, null));

        task.execute(executionInfo);

        InOrder inOrder = inOrder(azureBlobService, courtListPublisher);
        inOrder.verify(azureBlobService).uploadJson(anyString(), org.mockito.ArgumentMatchers.eq(CaTHService.buildBlobName(courtListId)));
        inOrder.verify(courtListPublisher).publish(anyString(), any(DtsMeta.class));
    }

    @Test
    void execute_continuesPublishing_whenBlobUploadFails() {
        doThrow(new RuntimeException("blob failed")).when(azureBlobService).uploadJson(anyString(), anyString());
        when(courtListPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(200);
        SjpListPayload payload = new SjpListPayload("2025-03-09T10:00:00", ONE_CASE);
        when(executionInfo.getJobData()).thenReturn(
                jobData(courtListId, SjpListType.SJP_PUBLIC_LIST.getValue(), payload, null, null));

        task.execute(executionInfo);

        verify(courtListPublisher).publish(anyString(), any(DtsMeta.class));
    }

    @Test
    void execute_skipsBlobUpload_whenBlobServiceNotAvailable() {
        SjpPublishTask taskWithoutBlob = new SjpPublishTask(
                new CourtListStatusUpdater(repository), new SjpToCathPayloadTransformer(), courtListPublisher, SANITIZER,
                jsonSchemaValidatorService, Optional.empty());
        when(courtListPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(200);
        SjpListPayload payload = new SjpListPayload("2025-03-09T10:00:00", ONE_CASE);
        when(executionInfo.getJobData()).thenReturn(
                jobData(courtListId, SjpListType.SJP_PUBLIC_LIST.getValue(), payload, null, null));

        taskWithoutBlob.execute(executionInfo);

        verify(azureBlobService, never()).uploadJson(anyString(), anyString());
        verify(courtListPublisher).publish(anyString(), any(DtsMeta.class));
    }

    // ── repeat publish: always overwrites, no content-based dedup ───────────

    @Test
    void execute_alwaysRepublishes_whenTriggeredAgainForSameRow_evenWithIdenticalContent() {
        // Same as the standard flow: a repeat trigger for the same day/list type reuses and
        // overwrites the same row (SjpCourtListPublishService resets it to REQUESTED before
        // queuing) — the task always re-transforms, re-uploads and re-sends, no dedup-skip.
        CourtListStatusEntity entity = new CourtListStatusEntity(
                courtListId, null, Status.REQUESTED, null,
                CourtListType.SJP_PUBLIC_FULL_ENGLISH, Instant.now());
        entity.setPublishDate(LocalDate.of(2025, 3, 9));
        when(repository.getByCourtListId(courtListId)).thenReturn(entity);
        when(courtListPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(200);
        SjpListPayload payload = new SjpListPayload("2025-03-09T10:00:00", ONE_CASE);
        when(executionInfo.getJobData()).thenReturn(
                jobData(courtListId, SjpListType.SJP_PUBLIC_LIST.getValue(), payload, null, null));

        task.execute(executionInfo);

        verify(azureBlobService).uploadJson(anyString(), anyString());
        verify(courtListPublisher).publish(anyString(), any(DtsMeta.class));
        assertThat(entity.getPublishStatus()).isEqualTo(Status.SUCCESSFUL);
    }

    @Test
    void execute_republishes_afterAPreviousFailure() {
        CourtListStatusEntity entity = new CourtListStatusEntity(
                courtListId, null, Status.FAILED, null,
                CourtListType.SJP_PUBLIC_FULL_ENGLISH, Instant.now());
        entity.setPublishDate(LocalDate.of(2025, 3, 9));
        when(repository.getByCourtListId(courtListId)).thenReturn(entity);
        when(courtListPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(200);
        SjpListPayload payload = new SjpListPayload("2025-03-09T10:00:00", ONE_CASE);
        when(executionInfo.getJobData()).thenReturn(
                jobData(courtListId, SjpListType.SJP_PUBLIC_LIST.getValue(), payload, null, null));

        task.execute(executionInfo);

        verify(courtListPublisher).publish(anyString(), any(DtsMeta.class));
        assertThat(entity.getPublishStatus()).isEqualTo(Status.SUCCESSFUL);
    }

    // ── status updates ───────────────────────────────────────────────────────

    @Test
    void execute_marksFailed_whenCathReturnsNonSuccessStatus() {
        when(courtListPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(500);
        CourtListStatusEntity entity = new CourtListStatusEntity(
                courtListId, null, Status.REQUESTED, null,
                CourtListType.SJP_PUBLIC_FULL_ENGLISH, Instant.now());
        entity.setPublishDate(LocalDate.of(2025, 3, 9));
        when(repository.getByCourtListId(courtListId)).thenReturn(entity);
        SjpListPayload payload = new SjpListPayload("2025-03-09T10:00:00", ONE_CASE);
        when(executionInfo.getJobData()).thenReturn(
                jobData(courtListId, SjpListType.SJP_PUBLIC_LIST.getValue(), payload, null, null));

        task.execute(executionInfo);

        assertThat(entity.getPublishStatus()).isEqualTo(Status.FAILED);
        assertThat(entity.getPublishErrorMessage()).contains("500");
    }

    @Test
    void execute_marksFailed_whenPublisherThrows() {
        doThrow(new RuntimeException("publish failed")).when(courtListPublisher).publish(anyString(), any(DtsMeta.class));
        CourtListStatusEntity entity = new CourtListStatusEntity(
                courtListId, null, Status.REQUESTED, null,
                CourtListType.SJP_PUBLIC_FULL_ENGLISH, Instant.now());
        entity.setPublishDate(LocalDate.of(2025, 3, 9));
        when(repository.getByCourtListId(courtListId)).thenReturn(entity);
        SjpListPayload payload = new SjpListPayload("2025-03-09T10:00:00", ONE_CASE);
        when(executionInfo.getJobData()).thenReturn(
                jobData(courtListId, SjpListType.SJP_PUBLIC_LIST.getValue(), payload, null, null));

        ExecutionInfo result = task.execute(executionInfo);

        assertThat(result.getExecutionStatus()).isEqualTo(COMPLETED);
        assertThat(entity.getPublishStatus()).isEqualTo(Status.FAILED);
        assertThat(entity.getPublishErrorMessage()).contains("publish failed");
    }

    // ── missing job data ─────────────────────────────────────────────────────

    @Test
    void execute_returnsCompleted_whenJobDataNull() {
        when(executionInfo.getJobData()).thenReturn(null);

        ExecutionInfo result = task.execute(executionInfo);

        assertThat(result.getExecutionStatus()).isEqualTo(COMPLETED);
        verify(courtListPublisher, never()).publish(anyString(), any(DtsMeta.class));
        verify(repository, never()).getByCourtListId(any());
    }

    @Test
    void execute_returnsCompleted_whenPayloadMissingFromJobData() {
        JsonObject jobData = Json.createObjectBuilder()
                .add(JobDataConstant.SJP_LIST_ID, courtListId.toString())
                .add(JobDataConstant.SJP_LIST_TYPE, SjpListType.SJP_PUBLIC_LIST.getValue())
                .build();
        when(executionInfo.getJobData()).thenReturn(jobData);

        ExecutionInfo result = task.execute(executionInfo);

        assertThat(result.getExecutionStatus()).isEqualTo(COMPLETED);
        verify(courtListPublisher, never()).publish(anyString(), any(DtsMeta.class));
    }

    // ── failure logs name the exact variant (press/public, delta/full, language) ──

    private final List<ListAppender<ILoggingEvent>> attachedAppenders = new java.util.ArrayList<>();

    private ListAppender<ILoggingEvent> attachTaskLogAppender() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        taskLogger().addAppender(appender);
        attachedAppenders.add(appender);
        return appender;
    }

    private static ch.qos.logback.classic.Logger taskLogger() {
        return (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(SjpPublishTask.class);
    }

    @AfterEach
    void detachAppenders() {
        attachedAppenders.forEach(appender -> {
            taskLogger().detachAppender(appender);
            appender.stop();
        });
        attachedAppenders.clear();
    }

    private String errorLog(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
    }

    @Test
    void execute_errorLogNamesDeltaPressVariantAndLanguage_whenPublisherThrows() {
        ListAppender<ILoggingEvent> appender = attachTaskLogAppender();
        doThrow(new RuntimeException("CaTH publish failed with HTTP status 500"))
                .when(courtListPublisher).publish(anyString(), any(DtsMeta.class));
        SjpListPayload payload = new SjpListPayload("2025-03-09T10:00:00", ONE_CASE, "325", true);
        when(executionInfo.getJobData()).thenReturn(
                jobData(courtListId, SjpListType.SJP_DELTA_PRESS_LIST.getValue(), payload, null, "DELTA"));

        task.execute(executionInfo);

        assertThat(errorLog(appender))
                .contains("listType=SJP_DELTA_PRESS_LIST")
                .contains("audience=PRESS")
                .contains("scope=DELTA")
                .contains("language=WELSH")
                .contains("requestType=DELTA")
                .contains("courtId=325")
                .contains(courtListId.toString());
    }

    @Test
    void execute_errorLogNamesFullPublicEnglishVariant_whenCathReturnsNonSuccessStatus() {
        ListAppender<ILoggingEvent> appender = attachTaskLogAppender();
        when(courtListPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(500);
        SjpListPayload payload = new SjpListPayload("2025-03-09T10:00:00", ONE_CASE);
        when(executionInfo.getJobData()).thenReturn(
                jobData(courtListId, SjpListType.SJP_PUBLIC_LIST.getValue(), payload, null, null));

        task.execute(executionInfo);

        assertThat(errorLog(appender))
                .contains("listType=SJP_PUBLIC_LIST")
                .contains("audience=PUBLIC")
                .contains("scope=FULL")
                .contains("language=ENGLISH")
                .contains("status: 500");
    }

    @Test
    void execute_errorLogFallsBackToRawJobData_whenPayloadCannotBeParsed() {
        ListAppender<ILoggingEvent> appender = attachTaskLogAppender();
        JsonObject jobData = Json.createObjectBuilder()
                .add(JobDataConstant.SJP_LIST_ID, courtListId.toString())
                .add(JobDataConstant.SJP_LIST_TYPE, SjpListType.SJP_DELTA_PUBLIC_LIST.getValue())
                .add(JobDataConstant.SJP_LANGUAGE, "WELSH")
                .add(JobDataConstant.SJP_REQUEST_TYPE, "DELTA")
                .add(JobDataConstant.SJP_PAYLOAD, "{not-json")
                .build();
        when(executionInfo.getJobData()).thenReturn(jobData);

        task.execute(executionInfo);

        assertThat(errorLog(appender))
                .contains("listType=SJP_DELTA_PUBLIC_LIST")
                .contains("language=WELSH")
                .contains("requestType=DELTA")
                .contains("context unresolved");
    }

    // ── status lifecycle: every attempt cycles REQUESTED -> SUCCESSFUL/FAILED ──

    @Test
    void execute_cyclesRowBackThroughRequested_thenSuccessful_whenPreviousAttemptFailed() {
        CourtListStatusEntity entity = new CourtListStatusEntity(
                courtListId, null, Status.FAILED, null,
                CourtListType.SJP_PUBLIC_FULL_ENGLISH, Instant.now());
        entity.setPublishDate(LocalDate.of(2025, 3, 9));
        entity.setPublishErrorMessage("previous CaTH failure stack trace");
        when(repository.getByCourtListId(courtListId)).thenReturn(entity);
        List<Status> savedStatuses = new java.util.ArrayList<>();
        when(repository.save(any(CourtListStatusEntity.class))).thenAnswer(invocation -> {
            savedStatuses.add(entity.getPublishStatus());
            return entity;
        });
        when(courtListPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(200);
        SjpListPayload payload = new SjpListPayload("2025-03-09T10:00:00", ONE_CASE);
        when(executionInfo.getJobData()).thenReturn(
                jobData(courtListId, SjpListType.SJP_PUBLIC_LIST.getValue(), payload, null, null));

        task.execute(executionInfo);

        assertThat(savedStatuses).containsExactly(Status.REQUESTED, Status.SUCCESSFUL);
        assertThat(entity.getPublishStatus()).isEqualTo(Status.SUCCESSFUL);
        assertThat(entity.getPublishErrorMessage()).isNull();
    }

    @Test
    void execute_doesNotRecordFailure_whenNewerAttemptAlreadyMarkedRowSuccessful() {
        // Queued jobs are assigned in batches to a thread pool, so a slow failing attempt can
        // finish after a newer attempt has already published successfully. The stale failure
        // must not overwrite that success.
        CourtListStatusEntity entity = new CourtListStatusEntity(
                courtListId, null, Status.FAILED, null,
                CourtListType.SJP_PUBLIC_FULL_ENGLISH, Instant.now());
        entity.setPublishDate(LocalDate.of(2025, 3, 9));
        when(repository.getByCourtListId(courtListId)).thenReturn(entity);
        when(courtListPublisher.publish(anyString(), any(DtsMeta.class))).thenAnswer(invocation -> {
            entity.setPublishStatus(Status.SUCCESSFUL);
            entity.setPublishErrorMessage(null);
            throw new RuntimeException("CaTH publish failed with HTTP status 504");
        });
        SjpListPayload payload = new SjpListPayload("2025-03-09T10:00:00", ONE_CASE);
        when(executionInfo.getJobData()).thenReturn(
                jobData(courtListId, SjpListType.SJP_PUBLIC_LIST.getValue(), payload, null, null));

        task.execute(executionInfo);

        assertThat(entity.getPublishStatus()).isEqualTo(Status.SUCCESSFUL);
        assertThat(entity.getPublishErrorMessage()).isNull();
    }
}
