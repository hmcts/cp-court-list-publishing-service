package uk.gov.hmcts.cp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.gov.hmcts.cp.domain.CourtListPublishStatusEntity;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishRequest;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishResponse;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.openapi.model.PublishStatus;
import uk.gov.hmcts.cp.services.CourtListPublishStatusService;

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

    @InjectMocks
    private CourtListPublishController controller;

    private ObjectMapper objectMapper;

    private static final String PUBLISH_URL = "/api/court-list-publish/publish";
    private static final String BASE_URL = "/api/court-list-publish";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    void createCourtListPublishStatus_shouldCreateEntity_whenValidRequestProvided() throws Exception {
        // Given
        CourtListPublishRequest request = createValidRequest();
        UUID expectedCourtListId = UUID.randomUUID();
        CourtListPublishStatusEntity expectedEntity = createEntity(
                expectedCourtListId,
                request.getCourtCentreId(),
                null,
                PublishStatus.PUBLISH_REQUESTED,
                request.getCourtListType()
        );

        when(service.createOrUpdate(
                any(UUID.class),
                any(UUID.class),
                any(PublishStatus.class),
                any(CourtListType.class)
        )).thenReturn(toResponse(expectedEntity));

        // When & Then
        mockMvc.perform(post(PUBLISH_URL)
                        .contentType(CONTENT_TYPE_APPLICATION_VND_POST)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(CONTENT_TYPE_APPLICATION_VND_POST))
                .andExpect(jsonPath("$.courtListId").exists())
                .andExpect(jsonPath("$.courtCentreId").value(request.getCourtCentreId().toString()))
                .andExpect(jsonPath("$.publishStatus").exists())
                .andExpect(jsonPath("$.publishStatus").value(PublishStatus.PUBLISH_REQUESTED.toString()))
                .andExpect(jsonPath("$.courtListType").value(request.getCourtListType().toString()))
                .andExpect(jsonPath("$.lastUpdated").exists());

        verify(service).createOrUpdate(
                any(UUID.class),
                eq(request.getCourtCentreId()),
                eq(PublishStatus.PUBLISH_REQUESTED),
                eq(request.getCourtListType())
        );
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
    void findCourtListPublishByCourtCenterId_shouldReturnList_whenEntitiesExist() throws Exception {
        // Given
        UUID courtCentreId = UUID.randomUUID();
        CourtListPublishStatusEntity entity1 = createEntity(UUID.randomUUID(), courtCentreId, null, PublishStatus.PUBLISH_REQUESTED, CourtListType.STANDARD);
        CourtListPublishStatusEntity entity2 = createEntity(UUID.randomUUID(), courtCentreId, null, PublishStatus.PUBLISH_REQUESTED, CourtListType.PUBLIC);
        List<CourtListPublishResponse> responses = List.of(
                toResponse(entity1),
                toResponse(entity2)
        );

        when(service.findByCourtCentreId(courtCentreId)).thenReturn(responses);

        // When & Then
        mockMvc.perform(get(BASE_URL + "/court-centre/" + courtCentreId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(CONTENT_TYPE_APPLICATION_VND_GET))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].courtCentreId").value(courtCentreId.toString()))
                .andExpect(jsonPath("$[1].courtCentreId").value(courtCentreId.toString()));

        verify(service).findByCourtCentreId(courtCentreId);
    }

    @Test
    void findCourtListPublishByCourtCenterId_shouldReturnEmptyList_whenNoEntitiesExist() throws Exception {
        // Given
        UUID courtCentreId = UUID.randomUUID();
        when(service.findByCourtCentreId(courtCentreId)).thenReturn(Collections.emptyList());

        // When & Then
        mockMvc.perform(get(BASE_URL + "/court-centre/" + courtCentreId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(CONTENT_TYPE_APPLICATION_VND_GET))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        verify(service).findByCourtCentreId(courtCentreId);
    }

    private CourtListPublishRequest createValidRequest() {
        return new CourtListPublishRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now(),
                LocalDate.now(),
                CourtListType.STANDARD
        );
    }

    private CourtListPublishStatusEntity createEntity(UUID courtListId, UUID courtCentreId, UUID courtRoomId,
                                                      PublishStatus publishStatus, CourtListType courtListType) {
        return new CourtListPublishStatusEntity(
                courtListId,
                courtCentreId,
                courtRoomId,
                publishStatus,
                courtListType,
                Instant.now()
        );
    }

    private CourtListPublishResponse toResponse(CourtListPublishStatusEntity entity) {
        OffsetDateTime lastUpdated = entity.getLastUpdated() != null
                ? entity.getLastUpdated().atOffset(ZoneOffset.UTC)
                : null;

        // Convert String publishStatus to PublishStatus enum
        PublishStatus publishStatusEnum = entity.getPublishStatus();

        return new CourtListPublishResponse(
                entity.getCourtListId(),
                entity.getCourtCentreId(),
                publishStatusEnum,
                entity.getCourtListType(),
                lastUpdated,
                entity.getCourtListFileId(),
                entity.getFileName(),
                entity.getErrorMessage(),
                entity.getPublishDate()
        );
    }
}

