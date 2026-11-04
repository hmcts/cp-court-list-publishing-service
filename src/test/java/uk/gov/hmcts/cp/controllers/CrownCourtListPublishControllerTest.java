package uk.gov.hmcts.cp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;
import uk.gov.hmcts.cp.models.CourtCentreData;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishRequest;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishResponse;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.openapi.model.Status;
import uk.gov.hmcts.cp.services.CourtListStatusUpdater;
import uk.gov.hmcts.cp.services.ReferenceDataService;
import uk.gov.hmcts.cp.services.crown.CrownListStatusService;
import uk.gov.hmcts.cp.services.crown.CrownListTaskTrigger;
import uk.gov.hmcts.cp.services.crown.CrownListType;
import uk.gov.hmcts.cp.services.crown.CrownPublishInProgressException;
import uk.gov.hmcts.cp.services.crown.CrownPublishRequest;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CrownCourtListPublishControllerTest {

    private static final String URL = "/api/court-list-publish/crown/publish";
    private static final String USER_ID = "test-user-id";
    private static final UUID COURT_CENTRE_ID = UUID.fromString("cc000000-0000-0000-0000-000000000001");
    private static final LocalDate MONDAY = LocalDate.of(2026, 6, 8);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 6, 14);

    @Mock
    private CrownListStatusService statusService;
    @Mock
    private CourtListStatusUpdater statusUpdater;
    @Mock
    private CrownListTaskTrigger taskTrigger;
    @Mock
    private ReferenceDataService referenceDataService;

    @InjectMocks
    private CrownCourtListPublishController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = ObjectMapperConfig.getObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @ParameterizedTest
    @CsvSource({"FINAL, 2026-06-10, 2026-06-10", "FIRM, 2026-06-08, 2026-06-14", "WARN, 2026-06-08, 2026-06-12"})
    void recordsThePublishAndQueuesTheTask(CourtListType courtListType, LocalDate startDate, LocalDate endDate) throws Exception {
        UUID courtListId = UUID.randomUUID();
        crownCourt();
        when(statusService.requestPublish(COURT_CENTRE_ID, courtListType, startDate)).thenReturn(response(courtListId, courtListType));

        perform(request(courtListType, startDate, endDate))
                .andExpect(status().isOk())
                .andExpect(content().contentType(CrownCourtListPublishController.CROWN_PUBLISH_MEDIA_TYPE))
                .andExpect(jsonPath("$.courtListId").value(courtListId.toString()))
                .andExpect(jsonPath("$.publishStatus").value("REQUESTED"));

        verify(taskTrigger).queue(new CrownPublishRequest(courtListId, COURT_CENTRE_ID,
                CrownListType.from(courtListType).orElseThrow(), startDate, endDate, USER_ID, "001", "0113 306 2800"));
        verifyNoInteractions(statusUpdater);
    }

    @Test
    void refusesWithBadRequestWhileTheSameListIsBeingPublished() throws Exception {
        crownCourt();
        when(statusService.requestPublish(COURT_CENTRE_ID, CourtListType.FIRM, MONDAY))
                .thenThrow(new CrownPublishInProgressException("A FIRM list publish starting 2026-06-08 is already in progress", null));

        perform(request(CourtListType.FIRM, MONDAY, SUNDAY))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason(containsString("already in progress")));
        verifyNoInteractions(taskTrigger, statusUpdater);
    }

    @Test
    void recordsAFailureAndReturns500WhenTheTaskCannotBeQueued() throws Exception {
        UUID courtListId = UUID.randomUUID();
        crownCourt();
        when(statusService.requestPublish(COURT_CENTRE_ID, CourtListType.FINAL, MONDAY)).thenReturn(response(courtListId, CourtListType.FINAL));
        IllegalStateException failure = new IllegalStateException("jobs table unavailable");
        doThrow(failure).when(taskTrigger).queue(any());

        perform(request(CourtListType.FINAL, MONDAY, MONDAY)).andExpect(status().isInternalServerError());

        verify(statusUpdater).markPublishFailed(courtListId, failure);
    }

    @ParameterizedTest
    @EnumSource(value = CourtListType.class, names = {"FINAL", "FIRM", "WARN"}, mode = EnumSource.Mode.EXCLUDE)
    void rejectsListTypesThatAreNotCrownLists(CourtListType courtListType) throws Exception {
        perform(request(courtListType, MONDAY, MONDAY)).andExpect(status().isBadRequest());
        verifyNothingRecorded();
    }

    @ParameterizedTest
    @CsvSource({"FINAL, 2026-06-08, 2026-06-09", "FIRM, 2026-06-09, 2026-06-14", "FIRM, 2026-06-08, 2026-06-15", "WARN, 2026-06-14, 2026-06-08"})
    void rejectsInvalidDates(CourtListType courtListType, LocalDate startDate, LocalDate endDate) throws Exception {
        perform(request(courtListType, startDate, endDate)).andExpect(status().isBadRequest());
        verifyNoInteractions(referenceDataService);
        verifyNothingRecorded();
    }

    @Test
    void rejectsACourtCentreThatIsNotACrownCourt() throws Exception {
        when(referenceDataService.getCourtCenterDataByCourtCentreId(COURT_CENTRE_ID.toString(), USER_ID))
                .thenReturn(Optional.of(CourtCentreData.builder().oucodeL1Code("B").courtIdNumeric("415").build()));

        perform(request(CourtListType.FINAL, MONDAY, MONDAY)).andExpect(status().isBadRequest());
        verifyNothingRecorded();
    }

    @Test
    void rejectsACrownCourtWithoutANumericCourtId() throws Exception {
        when(referenceDataService.getCourtCenterDataByCourtCentreId(COURT_CENTRE_ID.toString(), USER_ID))
                .thenReturn(Optional.of(CourtCentreData.builder().oucodeL1Code("C").build()));

        perform(request(CourtListType.FINAL, MONDAY, MONDAY)).andExpect(status().isBadRequest());
        verifyNothingRecorded();
    }

    @Test
    void rejectsACourtCentreMissingFromReferenceData() throws Exception {
        when(referenceDataService.getCourtCenterDataByCourtCentreId(COURT_CENTRE_ID.toString(), USER_ID)).thenReturn(Optional.empty());

        perform(request(CourtListType.FINAL, MONDAY, MONDAY)).andExpect(status().isBadRequest());
        verifyNothingRecorded();
    }

    @Test
    void rejectsARequestWithoutCjscppuid() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(CrownCourtListPublishController.CROWN_PUBLISH_MEDIA_TYPE)
                        .content(objectMapper.writeValueAsString(request(CourtListType.FIRM, MONDAY, SUNDAY))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(referenceDataService);
        verifyNothingRecorded();
    }

    private void crownCourt() {
        when(referenceDataService.getCourtCenterDataByCourtCentreId(COURT_CENTRE_ID.toString(), USER_ID))
                .thenReturn(Optional.of(CourtCentreData.builder().oucodeL1Code("C").courtIdNumeric("001").phone("0113 306 2800").build()));
    }

    private void verifyNothingRecorded() {
        verifyNoInteractions(statusService, taskTrigger, statusUpdater);
    }

    private ResultActions perform(CourtListPublishRequest request) throws Exception {
        return mockMvc.perform(post(URL)
                .header("CJSCPPUID", USER_ID)
                .contentType(CrownCourtListPublishController.CROWN_PUBLISH_MEDIA_TYPE)
                .content(objectMapper.writeValueAsString(request)));
    }

    private static CourtListPublishRequest request(CourtListType type, LocalDate startDate, LocalDate endDate) {
        return CourtListPublishRequest.builder().courtCentreId(COURT_CENTRE_ID).courtListType(type)
                .startDate(startDate).endDate(endDate).build();
    }

    private static CourtListPublishResponse response(UUID courtListId, CourtListType type) {
        return CourtListPublishResponse.builder().courtListId(courtListId).courtCentreId(COURT_CENTRE_ID)
                .courtListType(type).publishStatus(Status.REQUESTED).build();
    }
}
