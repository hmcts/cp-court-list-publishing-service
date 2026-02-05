package uk.gov.hmcts.cp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CourtListPublishControllerTest {

    public static final MediaType CONTENT_TYPE_APPLICATION_VND_POST = new MediaType("application", "vnd.courtlistpublishing-service.publish.post+json");
    public static final MediaType CONTENT_TYPE_APPLICATION_VND_GET = new MediaType("application", "vnd.courtlistpublishing-service.publish.get+json");

    private MockMvc mockMvc;

    @Mock
    private CourtListPublishStatusService service;

    @Mock
    private CourtListTaskTriggerService courtListTaskTriggerService;

    @InjectMocks
    private CourtListPublishController controller;

    private ObjectMapper objectMapper;

    private static final String PUBLISH_URL = "/api/court-list-publish/publish";
    private static final String BASE_URL = "/api/court-list-publish";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = ObjectMapperConfig.getObjectMapper();
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

        // When & Then (makeExternalCalls=true in payload for tests so task runs real CaTH/PDF flow when exercised)
        mockMvc.perform(post(PUBLISH_URL)
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
        verify(courtListTaskTriggerService).triggerCourtListTask(any(), eq(true));
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

    private CourtListPublishRequest createValidRequest() {
        return new CourtListPublishRequest(
                UUID.randomUUID(),  // courtCentreId
                LocalDate.now(),    // startDate
                LocalDate.now(),    // endDate
                CourtListType.STANDARD,
                true                // makeExternalCalls - tests expect external CaTH/PDF flow when exercised
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
                entity.getCourtListFileId(),
                entity.getFileUrl(),
                entity.getPublishErrorMessage(),
                entity.getFileErrorMessage(),
                entity.getPublishDate()
        );
    }
}

