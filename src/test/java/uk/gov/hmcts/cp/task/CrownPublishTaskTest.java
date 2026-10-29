package uk.gov.hmcts.cp.task;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.models.CourtCentreData;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.services.CaTHService;
import uk.gov.hmcts.cp.services.CourtListDataService;
import uk.gov.hmcts.cp.services.CourtListStatusUpdater;
import uk.gov.hmcts.cp.services.JsonSchemaValidatorService;
import uk.gov.hmcts.cp.services.ReferenceDataService;
import uk.gov.hmcts.cp.services.SchemaValidationException;
import uk.gov.hmcts.cp.services.courtlistdownload.CourtListDownloadException;
import uk.gov.hmcts.cp.services.crown.CrownAdvanceListTransformer;
import uk.gov.hmcts.cp.services.crown.CrownDailyListTransformer;
import uk.gov.hmcts.cp.services.crown.CrownFirmListTransformer;
import uk.gov.hmcts.cp.services.crown.CrownListTransformationService;
import uk.gov.hmcts.cp.services.crown.CrownListType;
import uk.gov.hmcts.cp.services.sanitization.WafPatternSanitizer;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo.executionInfo;

@ExtendWith(MockitoExtension.class)
class CrownPublishTaskTest {

    private static final UUID COURT_LIST_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String COURT_CENTRE_ID = "b2c3d4e5-f6a7-8901-bcde-f12345678901";
    private static final String USER_ID = "5b773ce6-7573-4979-8746-203778160d1e";
    private static final LocalDate START = LocalDate.parse("2026-11-02");
    private static final LocalDate END = LocalDate.parse("2026-11-08");

    @Mock
    private CourtListStatusUpdater statusUpdater;
    @Mock
    private CourtListDataService courtListDataService;
    @Mock
    private ReferenceDataService referenceDataService;
    @Mock
    private CaTHService cathService;

    private final CrownListTransformationService transformationService = new CrownListTransformationService(
            List.of(new CrownDailyListTransformer(), new CrownAdvanceListTransformer(), new CrownFirmListTransformer()),
            new WafPatternSanitizer("..\\,../"));
    private final JsonSchemaValidatorService schemaValidator = new JsonSchemaValidatorService();

    private CrownPublishTask task;
    private String payload;

    @BeforeEach
    void setUp() throws IOException {
        task = new CrownPublishTask(statusUpdater, courtListDataService, referenceDataService, transformationService,
                schemaValidator, cathService, true);
        try (InputStream in = getClass().getResourceAsStream("/crown/listing-crown-list-payload.json")) {
            payload = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void publishesEachListTypeAsItsCrownSchemaAndRecordsSuccess() {
        for (final Object[] row : new Object[][] {
                {CourtListType.FINAL, CrownListType.DAILY, "DailyList"},
                {CourtListType.WARN, CrownListType.ADVANCE, "WarnedList"},
                {CourtListType.FIRM, CrownListType.FIRM, "FirmList"}}) {
            final CourtListType courtListType = (CourtListType) row[0];
            final CrownListType crownListType = (CrownListType) row[1];
            stubListingAndReferenceData(courtListType);

            final ExecutionInfo result = task.execute(info(courtListType));

            assertThat(result.getExecutionStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            final ArgumentCaptor<String> document = ArgumentCaptor.forClass(String.class);
            verify(cathService).sendCrownListToCaTH(document.capture(), eq(crownListType), eq(START), eq(END), eq("100"), eq(COURT_LIST_ID));
            assertThat(document.getValue()).startsWith("{\"" + row[2] + "\":");
            verify(statusUpdater).markPublishSuccessful(COURT_LIST_ID);
            org.mockito.Mockito.clearInvocations(cathService, statusUpdater);
        }
    }

    @Test
    void queriesTheListingContextForTheCrownListWithTheCallersUserId() {
        stubListingAndReferenceData(CourtListType.WARN);

        task.execute(info(CourtListType.WARN));

        verify(courtListDataService).getCrownListPayload(CourtListType.WARN, COURT_CENTRE_ID, START, END, USER_ID);
        verify(referenceDataService).getCourtCenterDataByCourtCentreId(COURT_CENTRE_ID, USER_ID);
    }

    @Test
    void failsWhenThereIsNoReferenceDataForTheCourtCentre() {
        when(courtListDataService.getCrownListPayload(any(), anyString(), any(), any(), any())).thenReturn(payload);
        when(referenceDataService.getCourtCenterDataByCourtCentreId(anyString(), any())).thenReturn(Optional.empty());

        task.execute(info(CourtListType.FINAL));

        verifyNoInteractions(cathService);
        verify(statusUpdater).markPublishFailed(eq(COURT_LIST_ID), any(IllegalStateException.class));
    }

    @Test
    void failsWhenTheCourtCentreIsNotACrownCourt() {
        when(courtListDataService.getCrownListPayload(any(), anyString(), any(), any(), any())).thenReturn(payload);
        when(referenceDataService.getCourtCenterDataByCourtCentreId(anyString(), any())).thenReturn(Optional.of(
                CourtCentreData.builder().oucodeL1Code("B").courtIdNumeric("100").build()));

        task.execute(info(CourtListType.FINAL));

        verifyNoInteractions(cathService);
        verify(statusUpdater).markPublishFailed(eq(COURT_LIST_ID), any(IllegalStateException.class));
    }

    @Test
    void fallsBackToThePayloadCourtIdWhenReferenceDataHasNone() {
        when(courtListDataService.getCrownListPayload(any(), anyString(), any(), any(), any())).thenReturn(payload);
        when(referenceDataService.getCourtCenterDataByCourtCentreId(anyString(), any())).thenReturn(Optional.of(
                CourtCentreData.builder().oucodeL1Code("C").build()));

        task.execute(info(CourtListType.FINAL));

        verify(cathService).sendCrownListToCaTH(anyString(), eq(CrownListType.DAILY), eq(START), eq(END), eq("101"), eq(COURT_LIST_ID));
        verify(statusUpdater).markPublishSuccessful(COURT_LIST_ID);
    }

    @Test
    void recordsAFailureWhenCaThRejectsTheList() {
        stubListingAndReferenceData(CourtListType.FINAL);
        final RuntimeException failure = new RuntimeException("CaTH said no");
        org.mockito.Mockito.doThrow(failure).when(cathService).sendCrownListToCaTH(anyString(), any(), any(), any(), any(), any());

        final ExecutionInfo result = task.execute(info(CourtListType.FINAL));

        assertThat(result.getExecutionStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        verify(statusUpdater).markPublishFailed(COURT_LIST_ID, failure);
        verify(statusUpdater, never()).markPublishSuccessful(any());
    }

    @Test
    void recordsAFailureAndSendsNothingWhenTheListingContextCannotBeQueried() {
        final CourtListDownloadException failure = new CourtListDownloadException("listing is down");
        when(courtListDataService.getCrownListPayload(any(), anyString(), any(), any(), any())).thenThrow(failure);

        task.execute(info(CourtListType.FIRM));

        verify(statusUpdater).markPublishFailed(COURT_LIST_ID, failure);
        verifyNoInteractions(cathService);
    }

    @Test
    void recordsAFailureAndSendsNothingWhenTheListDoesNotMatchTheSchema() {
        final JsonSchemaValidatorService rejecting = org.mockito.Mockito.mock(JsonSchemaValidatorService.class);
        final SchemaValidationException failure = new SchemaValidationException("JSON schema validation failed: ListHeader is missing");
        org.mockito.Mockito.doThrow(failure).when(rejecting).validate(anyString(), any());
        task = new CrownPublishTask(statusUpdater, courtListDataService, referenceDataService, transformationService,
                rejecting, cathService, true);
        stubListingAndReferenceData(CourtListType.FINAL);

        task.execute(info(CourtListType.FINAL));

        verify(statusUpdater).markPublishFailed(COURT_LIST_ID, failure);
        verifyNoInteractions(cathService);
    }

    @Test
    void recordsAFailureWhenTheJobDataIsIncomplete() {
        final JsonObject incomplete = Json.createObjectBuilder()
                .add(JobDataConstant.COURT_LIST_ID, COURT_LIST_ID.toString())
                .add(JobDataConstant.COURT_LIST_TYPE, "FINAL")
                .build();

        task.execute(executionInfo().withJobData(incomplete).withAssignedTaskName(CrownPublishTask.TASK_NAME)
                .withExecutionStatus(ExecutionStatus.STARTED).build());

        verify(statusUpdater).markPublishFailed(eq(COURT_LIST_ID), any(IllegalStateException.class));
        verifyNoInteractions(courtListDataService, cathService);
    }

    @Test
    void recordsAFailureWithoutPublishingWhenCaThPublishingIsDisabled() {
        task = new CrownPublishTask(statusUpdater, courtListDataService, referenceDataService, transformationService,
                schemaValidator, cathService, false);

        final ExecutionInfo result = task.execute(info(CourtListType.FINAL));

        assertThat(result.getExecutionStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        verifyNoInteractions(courtListDataService, referenceDataService, cathService);
        verify(statusUpdater).markPublishFailed(eq(COURT_LIST_ID), any(IllegalStateException.class));
    }

    private void stubListingAndReferenceData(final CourtListType courtListType) {
        when(courtListDataService.getCrownListPayload(eq(courtListType), anyString(), any(), any(), any())).thenReturn(payload);
        when(referenceDataService.getCourtCenterDataByCourtCentreId(anyString(), any())).thenReturn(Optional.of(
                CourtCentreData.builder().oucodeL1Code("C").oucodeL3Name("Example Crown Court").courtIdNumeric("100").build()));
    }

    private static ExecutionInfo info(final CourtListType courtListType) {
        final JsonObjectBuilder jobData = Json.createObjectBuilder()
                .add(JobDataConstant.COURT_LIST_ID, COURT_LIST_ID.toString())
                .add(JobDataConstant.COURT_CENTRE_ID, COURT_CENTRE_ID)
                .add(JobDataConstant.COURT_LIST_TYPE, courtListType.toString())
                .add(JobDataConstant.START_DATE, START.toString())
                .add(JobDataConstant.END_DATE, END.toString())
                .add(JobDataConstant.USER_ID, USER_ID);
        return executionInfo().withJobData(jobData.build()).withAssignedTaskName(CrownPublishTask.TASK_NAME)
                .withExecutionStatus(ExecutionStatus.STARTED).build();
    }
}
