package uk.gov.hmcts.cp.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.gov.hmcts.cp.api.sjp.PublishSjpCourtListRequest;
import uk.gov.hmcts.cp.api.sjp.PublishSjpCourtListResponse;
import uk.gov.hmcts.cp.services.sjp.SjpCourtListPublishService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SjpCourtListPublishControllerTest {

    private static final String PUBLISH_COURTLIST_URL = "/api/court-list-publish/publishCourtList";

    private MockMvc mockMvc;

    @Mock
    private SjpCourtListPublishService sjpCourtListPublishService;

    @InjectMocks
    private SjpCourtListPublishController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void publishCourtList_returns200_withSjpPublishList() throws Exception {
        when(sjpCourtListPublishService.publishSjpCourtList(any(PublishSjpCourtListRequest.class)))
                .thenReturn(PublishSjpCourtListResponse.builder()
                        .status("ACCEPTED")
                        .listType(PublishSjpCourtListRequest.SJP_PUBLISH_LIST)
                        .message("SJP court list publish request accepted")
                        .build());

        mockMvc.perform(post(PUBLISH_COURTLIST_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listType\":\"SJP_PUBLISH_LIST\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.listType").value("SJP_PUBLISH_LIST"))
                .andExpect(jsonPath("$.message").value("SJP court list publish request accepted"));
    }

    @Test
    void publishCourtList_returns200_withSjpPressList() throws Exception {
        when(sjpCourtListPublishService.publishSjpCourtList(any(PublishSjpCourtListRequest.class)))
                .thenReturn(PublishSjpCourtListResponse.builder()
                        .status("ACCEPTED")
                        .listType(PublishSjpCourtListRequest.SJP_PRESS_LIST)
                        .message("SJP court list publish request accepted")
                        .build());

        mockMvc.perform(post(PUBLISH_COURTLIST_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listType\":\"SJP_PRESS_LIST\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listType").value("SJP_PRESS_LIST"));
    }

    @Test
    void publishCourtList_returns400_whenListTypeMissing() throws Exception {
        mockMvc.perform(post(PUBLISH_COURTLIST_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publishCourtList_returns400_whenListTypeInvalid() throws Exception {
        mockMvc.perform(post(PUBLISH_COURTLIST_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listType\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
    }
}
