package uk.gov.hmcts.cp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.gov.hmcts.cp.domain.CourtListStatusEntity;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishRequest;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishResponse;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.openapi.model.Status;
import uk.gov.hmcts.cp.services.CourtListPublishStatusService;
import uk.gov.hmcts.cp.services.CourtListTaskTriggerService;
import uk.gov.hmcts.cp.services.crown.CrownCourtListPublishService;
import uk.gov.hmcts.cp.services.sjp.SjpCourtListPublishService;
import uk.gov.hmcts.cp.services.sjp.SjpCourtListPublishService.SjpPublishResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import uk.gov.hmcts.cp.openapi.model.PublishCourtListRequest;
import uk.gov.hmcts.cp.openapi.model.SjpListPayload;
import uk.gov.hmcts.cp.openapi.model.SjpListType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CourtListPublishControllerTest {

    public static final MediaType CONTENT_TYPE_APPLICATION_VND_POST = new MediaType("application", "vnd.courtlistpublishing-service.publish.post+json");
    public static final MediaType CONTENT_TYPE_CROWN_PUBLISH_POST =
            MediaType.parseMediaType("application/vnd.courtlistpublishing-service.crown.publish.post+json");
    public static final MediaType CONTENT_TYPE_APPLICATION_VND_GET = new MediaType("application", "vnd.courtlistpublishing-service.publish.get+json");

    private MockMvc mockMvc;

    @Mock
    private CourtListPublishStatusService service;

    @Mock
    private CourtListTaskTriggerService courtListTaskTriggerService;


    @Mock
    private SjpCourtListPublishService sjpCourtListPublishService;

    @Mock
    private CrownCourtListPublishService crownCourtListPublishService;

    @InjectMocks
    private CourtListPublishController controller;


    private ObjectMapper objectMapper;

    private static final String PUBLISH_URL = "/api/court-list-publish/publish";
    private static final String SJP_PUBLISH_URL = "/api/court-list-publish/sjp/publishCourtList";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = ObjectMapperConfig.getObjectMapper();
    }

    @ParameterizedTest
    @EnumSource(value = CourtListType.class, names = {"FINAL", "FIRM", "WARN"})
    void publishCourtList_shouldRejectCrownListTypes_andPointToCrownMediaType(CourtListType crownListType) throws Exception {
        CourtListPublishRequest request = new CourtListPublishRequest(
                UUID.randomUUID(), LocalDate.now(), LocalDate.now(), crownListType, false);

        mockMvc.perform(post(PUBLISH_URL)
                        .header("CJSCPPUID", "test-user-id")
                        .contentType(CONTENT_TYPE_APPLICATION_VND_POST)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason(org.hamcrest.Matchers.containsString("application/vnd.courtlistpublishing-service.crown.publish.post+json")));

        verify(service, never()).createOrUpdate(any(), any(), any(), any());
        verify(courtListTaskTriggerService, never()).triggerCourtListTask(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = CourtListType.class, names = {"FINAL", "FIRM", "WARN"})
    void publishCourtList_shouldPublishThroughTheCrownService_whenContentTypeIsTheCrownMediaType(CourtListType crownListType) throws Exception {
        CourtListPublishRequest request = new CourtListPublishRequest(
                UUID.randomUUID(), LocalDate.now(), LocalDate.now(), crownListType, false);
        UUID courtListId = UUID.randomUUID();
        when(crownCourtListPublishService.publish(request, "test-user-id"))
                .thenReturn(CourtListPublishResponse.builder().courtListId(courtListId).courtCentreId(request.getCourtCentreId())
                        .courtListType(crownListType).publishStatus(Status.REQUESTED).build());

        mockMvc.perform(post(PUBLISH_URL)
                        .header("CJSCPPUID", "test-user-id")
                        .contentType(CONTENT_TYPE_CROWN_PUBLISH_POST)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(CONTENT_TYPE_CROWN_PUBLISH_POST))
                .andExpect(jsonPath("$.courtListId").value(courtListId.toString()))
                .andExpect(jsonPath("$.publishStatus").value("REQUESTED"));

        verify(service, never()).createOrUpdate(any(), any(), any(), any());
        verify(courtListTaskTriggerService, never()).triggerCourtListTask(any(), any());
    }

    @Test
    void publishCourtList_shouldReturnTheCrownServiceRefusal() throws Exception {
        CourtListPublishRequest request = new CourtListPublishRequest(
                UUID.randomUUID(), LocalDate.now(), LocalDate.now(), CourtListType.STANDARD, false);
        when(crownCourtListPublishService.publish(request, "test-user-id"))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "courtListType STANDARD cannot be published as a crown list"));

        mockMvc.perform(post(PUBLISH_URL)
                        .header("CJSCPPUID", "test-user-id")
                        .contentType(CONTENT_TYPE_CROWN_PUBLISH_POST)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason(org.hamcrest.Matchers.containsString("cannot be published as a crown list")));

        verify(service, never()).createOrUpdate(any(), any(), any(), any());
    }

    @Test
    void publishCourtList_shouldRequireCjscppuid_forTheCrownMediaType() throws Exception {
        CourtListPublishRequest request = new CourtListPublishRequest(
                UUID.randomUUID(), LocalDate.now(), LocalDate.now(), CourtListType.FINAL, false);

        mockMvc.perform(post(PUBLISH_URL)
                        .contentType(CONTENT_TYPE_CROWN_PUBLISH_POST)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(crownCourtListPublishService);
    }

    @Test
    void publishCourtList_shouldRejectTheOldCrownMediaType() throws Exception {
        CourtListPublishRequest request = new CourtListPublishRequest(
                UUID.randomUUID(), LocalDate.now(), LocalDate.now(), CourtListType.FINAL, false);

        mockMvc.perform(post(PUBLISH_URL)
                        .header("CJSCPPUID", "test-user-id")
                        .contentType(MediaType.parseMediaType("application/vnd.courtlistpublishing-service.crown.post+json"))
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(crownCourtListPublishService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "application/vnd.courtlistpublishing-service.crown.publish.post+json",
            "application/vnd.courtlistpublishing-service.crown.publish.post+json;charset=UTF-8"})
    void isCrownPublish_shouldMatchTheCrownMediaType(String contentType) {
        assertThat(CourtListPublishController.isCrownPublish(contentType)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"application/vnd.courtlistpublishing-service.publish.post+json", "not a media type"})
    void isCrownPublish_shouldNotMatchOtherContentTypes(String contentType) {
        assertThat(CourtListPublishController.isCrownPublish(contentType)).isFalse();
    }

    @Test
    void createCourtListPublishStatus_shouldCreateEntity_whenValidRequestProvided() throws Exception {
        // Given
        CourtListPublishRequest request = createValidRequest();
        UUID expectedCourtListId = UUID.randomUUID();
        CourtListStatusEntity expectedEntity = createEntity(
                expectedCourtListId,
                request.getCourtCentreId(),
                request.getCourtListType()
        );

        when(service.createOrUpdate(
                any(UUID.class),
                any(CourtListType.class),
                any(LocalDate.class),
                any(LocalDate.class)
        )).thenReturn(toResponse(expectedEntity));

        // When & Then
        mockMvc.perform(post(PUBLISH_URL)
                        .header("CJSCPPUID", "test-user-id")
                        .contentType(CONTENT_TYPE_APPLICATION_VND_POST)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(CONTENT_TYPE_APPLICATION_VND_POST))
                .andExpect(jsonPath("$.courtListId").exists())
                .andExpect(jsonPath("$.courtCentreId").value(request.getCourtCentreId().toString()))
                .andExpect(jsonPath("$.publishStatus").exists())
                .andExpect(jsonPath("$.publishStatus").value(Status.REQUESTED.toString()))
                .andExpect(jsonPath("$.courtListType").value(request.getCourtListType().toString()))
                .andExpect(jsonPath("$.lastUpdated").exists());

        verify(service).createOrUpdate(
                eq(request.getCourtCentreId()),
                eq(request.getCourtListType()),
                eq(request.getStartDate()),
                eq(request.getEndDate())
        );
        verify(courtListTaskTriggerService).triggerCourtListTask(any(), eq("test-user-id"));
    }

    @Test
    void createCourtList_shouldReturnBadRequest_whenCjscppuidHeaderMissing() throws Exception {
        // Given
        CourtListPublishRequest request = new CourtListPublishRequest()
                .courtCentreId(UUID.randomUUID())
                .courtListType(CourtListType.STANDARD)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(7));

        // When & Then - no CJSCPPUID header
        mockMvc.perform(post(PUBLISH_URL)
                        .contentType(CONTENT_TYPE_APPLICATION_VND_POST)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCourtList_shouldReturnBadRequest_whenRequestBodyIsNull() throws Exception {
        // When & Then
        mockMvc.perform(post(PUBLISH_URL)
                        .contentType(CONTENT_TYPE_APPLICATION_VND_POST)
                        .content(""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCourtListPublishStatus_shouldReturnBadRequest_whenCourtCentreIdIsMissing() throws Exception {
        // Given
        String requestBody = "{\"courtListType\":\"STANDARD\"}";

        // When & Then
        mockMvc.perform(post(PUBLISH_URL)
                        .contentType(CONTENT_TYPE_APPLICATION_VND_POST)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }



    @Test
    void createCourtListPublishStatus_shouldReturnBadRequest_whenCourtListTypeIsMissing() throws Exception {
        // Given
        String requestBody = "{\"courtCentreId\":\"" + UUID.randomUUID() + "\"}";

        // When & Then
        mockMvc.perform(post(PUBLISH_URL)
                        .contentType(CONTENT_TYPE_APPLICATION_VND_POST)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findCourtListPublishStatus_shouldReturnList_whenQueryingByCourtCentreIdAndPublishDate() throws Exception {
        // Given
        UUID courtCentreId = UUID.randomUUID();
        LocalDate publishDate = LocalDate.now();
        CourtListStatusEntity entity1 = createEntity(UUID.randomUUID(), courtCentreId, CourtListType.STANDARD);
        CourtListStatusEntity entity2 = createEntity(UUID.randomUUID(), courtCentreId, CourtListType.PUBLIC);
        List<CourtListPublishResponse> responses = List.of(
                toResponse(entity1),
                toResponse(entity2)
        );

        when(service.findPublishStatus(null, courtCentreId, publishDate, null)).thenReturn(responses);

        // When & Then
        mockMvc.perform(get("/api/court-list-publish/publish-status")
                        .param("courtCentreId", courtCentreId.toString())
                        .param("publishDate", publishDate.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(CONTENT_TYPE_APPLICATION_VND_GET))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].courtCentreId").value(courtCentreId.toString()))
                .andExpect(jsonPath("$[1].courtCentreId").value(courtCentreId.toString()));

        verify(service).findPublishStatus(null, courtCentreId, publishDate, null);
    }

    @Test
    void findCourtListPublishStatus_shouldReturnEmptyList_whenNoEntitiesExist() throws Exception {
        // Given
        UUID courtCentreId = UUID.randomUUID();
        LocalDate publishDate = LocalDate.now();
        when(service.findPublishStatus(null, courtCentreId, publishDate, null))
                .thenReturn(Collections.emptyList());

        // When & Then
        mockMvc.perform(get("/api/court-list-publish/publish-status")
                        .param("courtCentreId", courtCentreId.toString())
                        .param("publishDate", publishDate.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(CONTENT_TYPE_APPLICATION_VND_GET))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        verify(service).findPublishStatus(null, courtCentreId, publishDate, null);
    }

    // ── SJP publish endpoint ──────────────────────────────────────────────────

    private static final MediaType SJP_MEDIA_TYPE =
            new MediaType("application", "vnd.courtlistpublishing-service.sjp.post+json");

    private static SjpListPayload minimalSjpPayload() {
        return new SjpListPayload()
                .generatedDateAndTime("2025-03-09T10:00:00")
                .readyCases(List.of(java.util.Map.of(
                        "caseUrn", "URN1",
                        "defendantName", "D",
                        "prosecutorName", "P",
                        "sjpOffences", List.of(java.util.Map.of("title", "t", "wording", "w")))));
    }

    @Test
    void publishSjpCourtList_returnsOk_withVendorContentTypeAndAcceptedStatus() throws Exception {
        when(sjpCourtListPublishService.publishSjpCourtList(
                eq(SjpListType.SJP_PUBLIC_LIST), isNull(), isNull(), any()))
                .thenReturn(SjpPublishResult.accepted(SjpListType.SJP_PUBLIC_LIST, "SJP court list published to CaTH"));

        PublishCourtListRequest request = new PublishCourtListRequest()
                .listType(SjpListType.SJP_PUBLIC_LIST)
                .listPayload(minimalSjpPayload());

        mockMvc.perform(post(SJP_PUBLISH_URL)
                        .contentType(SJP_MEDIA_TYPE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(SJP_MEDIA_TYPE))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.listType").value("SJP_PUBLIC_LIST"));
    }

    @Test
    void publishSjpCourtList_passesLanguageAndRequestTypeToService() throws Exception {
        when(sjpCourtListPublishService.publishSjpCourtList(
                eq(SjpListType.SJP_PRESS_LIST), eq("WELSH"), eq("FULL"), any()))
                .thenReturn(SjpPublishResult.accepted(SjpListType.SJP_PRESS_LIST, "published"));

        PublishCourtListRequest request = new PublishCourtListRequest()
                .listType(SjpListType.SJP_PRESS_LIST)
                .language("WELSH")
                .requestType("FULL")
                .listPayload(minimalSjpPayload());

        mockMvc.perform(post(SJP_PUBLISH_URL)
                        .contentType(SJP_MEDIA_TYPE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(SJP_MEDIA_TYPE));

        verify(sjpCourtListPublishService).publishSjpCourtList(
                eq(SjpListType.SJP_PRESS_LIST), eq("WELSH"), eq("FULL"), any());
    }

    @Test
    void publishSjpCourtList_passesListPayloadToService() throws Exception {
        when(sjpCourtListPublishService.publishSjpCourtList(
                eq(SjpListType.SJP_PUBLIC_LIST), isNull(), isNull(), any()))
                .thenReturn(SjpPublishResult.accepted(SjpListType.SJP_PUBLIC_LIST, "published"));

        String requestJson = """
                {
                  "listType": "SJP_PUBLIC_LIST",
                  "listPayload": {
                    "generatedDateAndTime": "2025-03-09T10:00:00",
                    "isWelsh": true,
                    "courtIdNumeric": "325",
                    "readyCases": [
                      {
                        "caseUrn": "SJP-001",
                        "defendantName": "Test Defendant",
                        "prosecutorName": "CPS",
                        "sjpOffences": [{"title": "Offence", "wording": "Wording"}]
                      }
                    ]
                  }
                }
                """;

        mockMvc.perform(post(SJP_PUBLISH_URL)
                        .contentType(SJP_MEDIA_TYPE)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(content().contentType(SJP_MEDIA_TYPE));

        verify(sjpCourtListPublishService).publishSjpCourtList(
                eq(SjpListType.SJP_PUBLIC_LIST), isNull(), isNull(), any());
    }

    @Test
    void publishSjpCourtList_returnsBadRequest_whenListTypeMissing() throws Exception {
        mockMvc.perform(post(SJP_PUBLISH_URL)
                        .contentType(SJP_MEDIA_TYPE)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publishSjpCourtList_returnsOk_withFailedStatus_whenServiceFails() throws Exception {
        when(sjpCourtListPublishService.publishSjpCourtList(any(), any(), any(), any()))
                .thenReturn(SjpPublishResult.failed(SjpListType.SJP_PUBLIC_LIST, "CaTH returned status 500"));

        PublishCourtListRequest request = new PublishCourtListRequest()
                .listType(SjpListType.SJP_PUBLIC_LIST)
                .listPayload(minimalSjpPayload());

        mockMvc.perform(post(SJP_PUBLISH_URL)
                        .contentType(SJP_MEDIA_TYPE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(SJP_MEDIA_TYPE))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.message").value("CaTH returned status 500"));
    }

    private CourtListPublishRequest createValidRequest() {
        return new CourtListPublishRequest(
                UUID.randomUUID(),  // courtCentreId
                LocalDate.now(),    // startDate
                LocalDate.now(),    // endDate
                CourtListType.STANDARD,
                Boolean.FALSE       // restricted
        );
    }

    private CourtListStatusEntity createEntity(UUID courtListId, UUID courtCentreId,
                                               CourtListType courtListType) {
        return new CourtListStatusEntity(
                courtListId,
                courtCentreId,
                Status.REQUESTED,
                Status.REQUESTED,
                courtListType,
                Instant.now()
        );
    }

    private CourtListPublishResponse toResponse(CourtListStatusEntity entity) {
        OffsetDateTime lastUpdated = entity.getLastUpdated() != null
                ? entity.getLastUpdated().atOffset(ZoneOffset.UTC)
                : null;

        // Convert String publishStatus to Status enum
        Status publishStatusEnum = entity.getPublishStatus();
        Status fileStatusEnum = entity.getFileStatus();

        return new CourtListPublishResponse(
                entity.getCourtListId(),
                entity.getCourtCentreId(),
                publishStatusEnum,
                fileStatusEnum,
                entity.getCourtListType(),
                lastUpdated,
                entity.getFileUrl(),
                entity.getFileId(),
                entity.getPublishErrorMessage(),
                entity.getFileErrorMessage(),
                entity.getPublishDate()
        );
    }
}

