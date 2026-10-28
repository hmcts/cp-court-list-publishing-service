package uk.gov.hmcts.cp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishRequest;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishResponse;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.openapi.model.Status;
import uk.gov.hmcts.cp.services.crown.CrownCourtListPublishService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CrownCourtListPublishControllerTest {

    private static final MediaType CROWN_MEDIA_TYPE = new MediaType("application", "vnd.courtlistpublishing-service.crown.post+json");
    private static final String URL = "/api/court-list-publish/crown/publish";
    private static final UUID COURT_CENTRE_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final LocalDate START = LocalDate.parse("2026-11-02");
    private static final LocalDate END = LocalDate.parse("2026-11-08");

    @Mock
    private CrownCourtListPublishService service;

    @InjectMocks
    private CrownCourtListPublishController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = ObjectMapperConfig.getObjectMapper();
    }

    @Test
    void acceptsARequestAndReturnsTheStatusInTheCrownMediaType() throws Exception {
        final UUID courtListId = UUID.randomUUID();
        when(service.publish(COURT_CENTRE_ID, CourtListType.FIRM, START, END, "user-1")).thenReturn(new CourtListPublishResponse(
                courtListId, COURT_CENTRE_ID, Status.REQUESTED, null, CourtListType.FIRM, OffsetDateTime.now(), null, null, null, null, START));

        mockMvc.perform(post(URL)
                        .header("CJSCPPUID", "user-1")
                        .contentType(CROWN_MEDIA_TYPE)
                        .content(objectMapper.writeValueAsString(request(CourtListType.FIRM))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(CROWN_MEDIA_TYPE))
                .andExpect(jsonPath("$.courtListId").value(courtListId.toString()))
                .andExpect(jsonPath("$.courtCentreId").value(COURT_CENTRE_ID.toString()))
                .andExpect(jsonPath("$.courtListType").value("FIRM"))
                .andExpect(jsonPath("$.publishStatus").value("REQUESTED"));

        verify(service).publish(COURT_CENTRE_ID, CourtListType.FIRM, START, END, "user-1");
    }

    @Test
    void rejectsAMissingUserHeaderWithoutCallingTheService() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(CROWN_MEDIA_TYPE)
                        .content(objectMapper.writeValueAsString(request(CourtListType.FINAL))))
                .andExpect(status().isBadRequest());

        verify(service, never()).publish(any(), any(), any(), any(), any());
    }

    @Test
    void returnsBadRequestWhenTheServiceRejectsTheCourtListType() throws Exception {
        when(service.publish(any(), any(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot be published as a Crown list"));

        mockMvc.perform(post(URL)
                        .header("CJSCPPUID", "user-1")
                        .contentType(CROWN_MEDIA_TYPE)
                        .content(objectMapper.writeValueAsString(request(CourtListType.STANDARD))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyAcceptsTheCrownMediaType() throws Exception {
        mockMvc.perform(post(URL)
                        .header("CJSCPPUID", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(CourtListType.FINAL))))
                .andExpect(status().isUnsupportedMediaType());

        verify(service, never()).publish(any(), any(), any(), any(), any());
    }

    private static CourtListPublishRequest request(final CourtListType type) {
        final CourtListPublishRequest request = new CourtListPublishRequest();
        request.setCourtCentreId(COURT_CENTRE_ID);
        request.setCourtListType(type);
        request.setStartDate(START);
        request.setEndDate(END);
        return request;
    }
}
