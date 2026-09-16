package uk.gov.hmcts.cp.services.sjp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.gov.hmcts.cp.cleanup.CleanupJobService;
import uk.gov.hmcts.cp.controllers.CourtListPublishController;
import uk.gov.hmcts.cp.domain.DtsMeta;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.repositories.CourtListStatusRepository;
import uk.gov.hmcts.cp.services.CourtListPublishStatusService;
import uk.gov.hmcts.cp.services.CourtListPublisher;
import uk.gov.hmcts.cp.services.CourtListStatusUpdater;
import uk.gov.hmcts.cp.services.CourtListTaskTriggerService;
import uk.gov.hmcts.cp.services.JsonSchemaValidatorService;
import uk.gov.hmcts.cp.services.ReferenceDataService;
import uk.gov.hmcts.cp.services.courtlistdownload.CourtListDownloadService;
import uk.gov.hmcts.cp.services.sanitization.DocumentSanitizer;
import uk.gov.hmcts.cp.services.sanitization.HtmlStrippingSanitizer;
import uk.gov.hmcts.cp.services.sanitization.RequiredStringFieldsRegistry;
import uk.gov.hmcts.cp.services.sanitization.WafPatternSanitizer;
import uk.gov.hmcts.cp.task.SjpPublishTask;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.service.ExecutionService;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the SJP → CaTH publish flow when CATH_PUBLISHING_ENABLED=false.
 * Wires the real SjpCourtListPublishService with cathPublishingEnabled=false so the
 * feature flag is exercised through the full HTTP stack (controller → service → guard).
 * SjpTaskTriggerService is mocked to confirm the async publish job is never queued
 * when publishing is disabled.
 */
@ExtendWith(MockitoExtension.class)
class SjpCathPublishingDisabledIntegrationTest {

    private static final MediaType SJP_CONTENT_TYPE =
            MediaType.parseMediaType("application/vnd.courtlistpublishing-service.sjp.post+json");
    private static final String SJP_PUBLISH_URL = "/api/court-list-publish/sjp/publishCourtList";

    private static final DocumentSanitizer SANITIZER = new DocumentSanitizer(
            new WafPatternSanitizer("..\\.\\,../"),
            new HtmlStrippingSanitizer(),
            new RequiredStringFieldsRegistry());

    @Mock private CourtListStatusRepository courtListStatusRepository;
    @Mock private ExecutionService executionService;
    @Mock private CourtListPublisher courtListPublisher;
    @Mock private JsonSchemaValidatorService jsonSchemaValidatorService;
    @Mock private CourtListPublishStatusService service;
    @Mock private CourtListTaskTriggerService courtListTaskTriggerService;
    @Mock private CourtListDownloadService courtListDownloadService;
    @Mock private CleanupJobService cleanupJobService;
    @Mock private ReferenceDataService referenceDataService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        lenient().when(courtListStatusRepository.findByPublishDateAndCourtListType(
                        any(LocalDate.class), any(CourtListType.class)))
                .thenReturn(Optional.empty());

        SjpTaskTriggerService sjpTaskTriggerService = new SjpTaskTriggerService(executionService);
        SjpCourtListPublishService sjpService =
                new SjpCourtListPublishService(courtListStatusRepository, sjpTaskTriggerService);

        CourtListPublishController controller = new CourtListPublishController(
                service,
                courtListTaskTriggerService,
                courtListDownloadService,
                cleanupJobService,
                sjpService,
                referenceDataService
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** Posts the SJP publish request, returning the {@link ExecutionInfo} handed to the task manager. */
    private ExecutionInfo publishAndCaptureQueuedExecution(String requestJson, String expectedListType) throws Exception {
        mockMvc.perform(post(SJP_PUBLISH_URL)
                        .contentType(SJP_CONTENT_TYPE)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.listType").value(expectedListType))
                .andExpect(jsonPath("$.message").value("SJP court list publish request accepted for processing"));

        ArgumentCaptor<ExecutionInfo> executionInfoCaptor = ArgumentCaptor.forClass(ExecutionInfo.class);
        verify(executionService).executeWith(executionInfoCaptor.capture());
        return executionInfoCaptor.getValue();
    }

    /** The task, as the task manager would build it, with CATH_PUBLISHING_ENABLED=false. */
    private SjpPublishTask disabledTask() {
        return new SjpPublishTask(
                new CourtListStatusUpdater(courtListStatusRepository),
                new SjpToCathPayloadTransformer(),
                courtListPublisher,
                SANITIZER,
                jsonSchemaValidatorService,
                Optional.empty(),
                false // CATH_PUBLISHING_ENABLED=false
        );
    }

    @Test
    void publishSjpCourtList_queuesTask_thenTaskSkipsCaTHSend_whenCathPublishingDisabled() throws Exception {
        String requestJson = """
                {
                  "listType": "SJP_PUBLIC_LIST",
                  "listPayload": {
                    "generatedDateAndTime": "2025-03-09T10:00:00",
                    "readyCases": [
                      {
                        "caseUrn": "URN001",
                        "defendantName": "John Smith",
                        "prosecutorName": "CPS",
                        "sjpOffences": [{"title": "Speeding", "wording": "Drove at 90mph in a 70 zone"}]
                      }
                    ]
                  }
                }
                """;

        ExecutionInfo queuedExecution = publishAndCaptureQueuedExecution(requestJson, "SJP_PUBLIC_LIST");

        ExecutionInfo result = disabledTask().execute(queuedExecution);

        assertThat(result).isNotNull();
        verify(courtListPublisher, never()).publish(anyString(), any(DtsMeta.class));
    }

    @Test
    void publishSjpPressCourtList_queuesTask_thenTaskSkipsCaTHSend_whenCathPublishingDisabled() throws Exception {
        String requestJson = """
                {
                  "listType": "SJP_PRESS_LIST",
                  "listPayload": {
                    "generatedDateAndTime": "2025-03-09T10:00:00",
                    "readyCases": [
                      {
                        "caseUrn": "URN002",
                        "defendantName": "Jane Doe",
                        "prosecutorName": "CPS",
                        "sjpOffences": [{"title": "Littering", "wording": "Dropped litter in public"}]
                      }
                    ]
                  }
                }
                """;

        ExecutionInfo queuedExecution = publishAndCaptureQueuedExecution(requestJson, "SJP_PRESS_LIST");

        ExecutionInfo result = disabledTask().execute(queuedExecution);

        assertThat(result).isNotNull();
        verify(courtListPublisher, never()).publish(anyString(), any(DtsMeta.class));
    }
}
