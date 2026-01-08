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
import uk.gov.hmcts.cp.models.transformed.CourtListDocument;
import uk.gov.hmcts.cp.services.CourtListQueryService;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CourtListQueryControllerTest {

    public static final String USER_ID = "ad0920bd-521a-4f40-b942-f82d258ea3cc";
    private MockMvc mockMvc;

    @Mock
    private CourtListQueryService courtListQueryService;

    @InjectMocks
    private CourtListQueryController controller;

    private static final String QUERY_URL = "/api/court-list/query";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void queryCourtList_shouldReturnTransformedDocument_whenValidParametersProvided() throws Exception {
        // Given
        String listId = "STANDARD";
        String courtCentreId = "f8254db1-1683-483e-afb3-b87fde5a0a26";
        String startDate = "2026-01-05";
        String endDate = "2026-01-12";

        CourtListDocument document = createMockDocument();

        when(courtListQueryService.queryCourtList(listId, courtCentreId, startDate, endDate, USER_ID))
                .thenReturn(document);

        // When & Then
        mockMvc.perform(get(QUERY_URL)
                        .param("listId", listId)
                        .param("courtCentreId", courtCentreId)
                        .param("startDate", startDate)
                        .param("endDate", endDate)
                        .header("CJSCPPUID", USER_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.document").exists())
                .andExpect(jsonPath("$.document.info").exists())
                .andExpect(jsonPath("$.document.data").exists());

        verify(courtListQueryService).queryCourtList(eq(listId), eq(courtCentreId), eq(startDate), eq(endDate), eq(USER_ID));
    }

    @Test
    void queryCourtList_shouldReturnInternalServerError_whenServiceThrowsException() throws Exception {
        // Given
        String listId = "STANDARD";
        String courtCentreId = "f8254db1-1683-483e-afb3-b87fde5a0a26";
        String startDate = "2026-01-05";
        String endDate = "2026-01-12";

        when(courtListQueryService.queryCourtList(listId, courtCentreId, startDate, endDate, USER_ID))
                .thenThrow(new RuntimeException("External API error"));

        // When & Then
        mockMvc.perform(get(QUERY_URL)
                        .param("listId", listId)
                        .param("courtCentreId", courtCentreId)
                        .param("startDate", startDate)
                        .param("endDate", endDate)
                        .header("CJSCPPUID", USER_ID))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void queryCourtList_shouldReturnBadRequest_whenCourtCentreIdIsEmpty() throws Exception {
        // When & Then
        mockMvc.perform(get(QUERY_URL)
                        .param("listId", "STANDARD")
                        .param("courtCentreId", "")
                        .param("startDate", "2026-01-05")
                        .param("endDate", "2026-01-12"))
                .andExpect(status().isBadRequest());
    }


    private CourtListDocument createMockDocument() {
        return CourtListDocument.builder()
                .document(uk.gov.hmcts.cp.models.transformed.Document.builder()
                        .info(uk.gov.hmcts.cp.models.transformed.DocumentInfo.builder()
                                .startTime("10:00:00")
                                .build())
                        .data(uk.gov.hmcts.cp.models.transformed.DocumentData.builder()
                                .job(uk.gov.hmcts.cp.models.transformed.Job.builder()
                                        .printDate("01/01/2026")
                                        .build())
                                .build())
                        .build())
                .build();
    }
}

