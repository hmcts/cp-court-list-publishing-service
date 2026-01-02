package uk.gov.hmcts.cp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.gov.hmcts.cp.domain.CourtListPublishStatusEntity;
import uk.gov.hmcts.cp.dto.CourtListPublishRequest;
import uk.gov.hmcts.cp.dto.CourtListPublishResponse;
import uk.gov.hmcts.cp.services.CourtListPublishStatusService;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
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
        objectMapper = new ObjectMapper();
    }

    @Test
    void createCourtListPublishStatus_shouldCreateEntity_whenValidRequestProvided() throws Exception {
        // Given
        CourtListPublishRequest request = createValidRequest();
        CourtListPublishStatusEntity expectedEntity = createEntityFromRequest(request);

        when(service.createOrUpdate(
                any(UUID.class),
                any(UUID.class),
                any(String.class),
                any(String.class)
        )).thenReturn(CourtListPublishResponse.from(expectedEntity));

        // When & Then
        mockMvc.perform(post(PUBLISH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(CONTENT_TYPE_APPLICATION_VND_POST))
                .andExpect(jsonPath("$.courtListId").value(request.courtListId().toString()))
                .andExpect(jsonPath("$.courtCentreId").value(request.courtCentreId().toString()))
                .andExpect(jsonPath("$.publishStatus").exists())
                .andExpect(jsonPath("$.publishStatus").value(request.publishStatus()))
                .andExpect(jsonPath("$.courtListType").value(request.courtListType()))
                .andExpect(jsonPath("$.lastUpdated").exists());

        verify(service).createOrUpdate(
                request.courtListId(),
                request.courtCentreId(),
                request.publishStatus(),
                request.courtListType()
        );
    }

    @Test
    void createCourtList_shouldReturnBadRequest_whenRequestBodyIsNull() throws Exception {
        // When & Then
        mockMvc.perform(post(PUBLISH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCourtListPublishStatus_shouldReturnBadRequest_whenCourtListIdIsMissing() throws Exception {
        // Given
        String requestBody = "{\"courtCentreId\":\"" + UUID.randomUUID() + "\"," +
                "\"publishStatus\":\"PUBLISHED\"," +
                "\"courtListType\":\"DAILY\"}";

        // When & Then
        mockMvc.perform(post(PUBLISH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCourtListPublishStatus_shouldReturnBadRequest_whenCourtCentreIdIsMissing() throws Exception {
        // Given
        String requestBody = "{\"courtListId\":\"" + UUID.randomUUID() + "\"," +
                "\"publishStatus\":\"PUBLISHED\"," +
                "\"courtListType\":\"DAILY\"}";

        // When & Then
        mockMvc.perform(post(PUBLISH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCourtListPublishStatus_shouldReturnBadRequest_whenIsMissing() throws Exception {
        // Given
        String requestBody = "{\"courtListId\":\"" + UUID.randomUUID() + "\"," +
                "\"courtCentreId\":\"" + UUID.randomUUID() + "\"," +
                "\"courtListType\":\"DAILY\"}";

        // When & Then
        mockMvc.perform(post(PUBLISH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCourtListPublishStatus_shouldReturnBadRequest_whenIsBlank() throws Exception {
        // Given
        String requestBody = "{\"courtListId\":\"" + UUID.randomUUID() + "\"," +
                "\"courtCentreId\":\"" + UUID.randomUUID() + "\"," +
                "\"publishStatus\":\"   \"," +
                "\"courtListType\":\"DAILY\"}";

        // When & Then
        mockMvc.perform(post(PUBLISH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCourtListPublishStatus_shouldReturnBadRequest_whenCourtListTypeIsMissing() throws Exception {
        // Given
        String requestBody = "{\"courtListId\":\"" + UUID.randomUUID() + "\"," +
                "\"courtCentreId\":\"" + UUID.randomUUID() + "\"," +
                "\"publishStatus\":\"PUBLISHED\"}";

        // When & Then
        mockMvc.perform(post(PUBLISH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCourtListPublishStatus_shouldReturnBadRequest_whenCourtListTypeIsBlank() throws Exception {
        // Given
        String requestBody = "{\"courtListId\":\"" + UUID.randomUUID() + "\"," +
                "\"courtCentreId\":\"" + UUID.randomUUID() + "\"," +
                "\"publishStatus\":\"PUBLISHED\"," +
                "\"courtListType\":\"   \"}";

        // When & Then
        mockMvc.perform(post(PUBLISH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findCourtListPublishByCourtCenterId_shouldReturnList_whenEntitiesExist() throws Exception {
        // Given
        UUID courtCentreId = UUID.randomUUID();
        CourtListPublishStatusEntity entity1 = createEntity(UUID.randomUUID(), courtCentreId, "PUBLISHED", "DAILY");
        CourtListPublishStatusEntity entity2 = createEntity(UUID.randomUUID(), courtCentreId, "PENDING", "WEEKLY");
        List<CourtListPublishResponse> responses = List.of(
                CourtListPublishResponse.from(entity1),
                CourtListPublishResponse.from(entity2)
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
                "PUBLISHED",
                "DAILY"
        );
    }

    private CourtListPublishStatusEntity createEntityFromRequest(CourtListPublishRequest request) {
        return new CourtListPublishStatusEntity(
                request.courtListId(),
                request.courtCentreId(),
                request.publishStatus(),
                request.courtListType(),
                LocalDateTime.now()
        );
    }

    private CourtListPublishStatusEntity createEntity(UUID courtListId, UUID courtCentreId, 
                                                      String publishStatus, String courtListType) {
        return new CourtListPublishStatusEntity(
                courtListId,
                courtCentreId,
                publishStatus,
                courtListType,
                LocalDateTime.now()
        );
    }
}

