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
import uk.gov.hmcts.cp.dto.HearingRequest;
import uk.gov.hmcts.cp.services.HearingService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HearingControllerTest {

    public static final String VND_COURTLISTPUBLISHING_SERVICE_HEARING_GET_JSON = "application/vnd.courtlistpublishing-service.hearing.get+json";
    public static final String VND_COURTLISTPUBLISHING_SERVICE_HEARING_POST_JSON = "application/vnd.courtlistpublishing-service.hearing.post+json";
    private MockMvc mockMvc;

    @Mock
    private HearingService hearingService;

    @InjectMocks
    private HearingController hearingController;

    private ObjectMapper objectMapper;

    private static final String BASE_URL = "/api/hearing";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(hearingController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void getHearingData_shouldReturnHearingData_whenValidHearingIdProvided() throws Exception {
        // Given
        UUID hearingId = UUID.randomUUID();
        String expectedResponse = "{\"hearingId\":\"" + hearingId + "\",\"payload\":\"test data\"}";

        when(hearingService.getHearingById(hearingId)).thenReturn(expectedResponse);

        // When & Then
        mockMvc.perform(get(BASE_URL + "/{hearingId}", hearingId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(VND_COURTLISTPUBLISHING_SERVICE_HEARING_GET_JSON))
                .andExpect(content().string(expectedResponse));
    }

    @Test
    void getHearingData_shouldReturnBadRequest_whenInvalidUUIDProvided() throws Exception {
        // When & Then
        mockMvc.perform(get(BASE_URL + "/{hearingId}", "invalid-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postHearing_shouldCreateHearing_whenValidRequestProvided() throws Exception {
        // Given
        HearingRequest hearingRequest = createHearingRequest();
        String expectedResponse = "{\"hearingId\":\"" + UUID.randomUUID() + "\",\"payload\":\"test payload\"}";

        when(hearingService.updateHearing(any(HearingRequest.class))).thenReturn(expectedResponse);

        // When & Then
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hearingRequest)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(VND_COURTLISTPUBLISHING_SERVICE_HEARING_POST_JSON))
                .andExpect(content().string(expectedResponse));
    }

    @Test
    void postHearing_shouldReturnBadRequest_whenRequestBodyIsNull() throws Exception {
        // When & Then
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postHearing_shouldReturnBadRequest_whenHearingIdIsMissing() throws Exception {
        // Given
        String requestBody = "{\"payload\":\"test payload\"}";

        // When & Then
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }



    @Test
    void postHearing_shouldReturnBadRequest_whenPayloadIsMissing() throws Exception {
        // Given
        UUID hearingId = UUID.randomUUID();
        String requestBody = "{\"hearingId\":\"" + hearingId + "\"}";

        // When & Then
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }



    private HearingRequest createHearingRequest() {
        UUID hearingId = UUID.randomUUID();
        String payload = "{\"hearingId\":\"12345\",\"payload\":\"954a8c30-5bca-4b3b-9078-171ca1f762ed\"}";
        return new HearingRequest(hearingId, payload);
    }
}