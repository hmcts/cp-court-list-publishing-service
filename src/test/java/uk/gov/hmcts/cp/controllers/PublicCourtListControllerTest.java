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
import uk.gov.hmcts.cp.services.publiccourtlist.PublicCourtListException;
import uk.gov.hmcts.cp.services.publiccourtlist.PublicCourtListService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PublicCourtListControllerTest {

    private static final String COURT_CENTRE_ID = "f8254db1-1683-483e-afb3-b87fde5a0a26";
    private static final String START_DATE = "2026-02-27";
    private static final String END_DATE = "2026-02-27";
    private static final String BASE_URL = "/api/public-court-list";
    private static final byte[] PDF_BYTES = "PDF content".getBytes();

    private MockMvc mockMvc;

    @Mock
    private PublicCourtListService publicCourtListService;

    @InjectMocks
    private PublicCourtListController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getPublicCourtListPdf_returnsPdf_whenValidParams() throws Exception {
        when(publicCourtListService.generatePublicCourtListPdf(
                eq(COURT_CENTRE_ID),
                any(),
                any()))
                .thenReturn(PDF_BYTES);

        mockMvc.perform(get(BASE_URL)
                        .param("courtCentreId", COURT_CENTRE_ID)
                        .param("startDate", START_DATE)
                        .param("endDate", END_DATE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"CourtList.pdf\""))
                .andExpect(content().bytes(PDF_BYTES));

        verify(publicCourtListService).generatePublicCourtListPdf(eq(COURT_CENTRE_ID), any(), any());
    }

    @Test
    void getPublicCourtListPdf_returns400_whenCourtCentreIdMissing() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .param("startDate", START_DATE)
                        .param("endDate", END_DATE))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPublicCourtListPdf_returns400_whenCourtCentreIdBlank() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .param("courtCentreId", " ")
                        .param("startDate", START_DATE)
                        .param("endDate", END_DATE))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPublicCourtListPdf_returns400_whenStartDateMissing() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .param("courtCentreId", COURT_CENTRE_ID)
                        .param("endDate", END_DATE))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPublicCourtListPdf_returns400_whenEndDateMissing() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .param("courtCentreId", COURT_CENTRE_ID)
                        .param("startDate", START_DATE))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPublicCourtListPdf_returns400_whenEndDateBeforeStartDate() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .param("courtCentreId", COURT_CENTRE_ID)
                        .param("startDate", "2026-02-28")
                        .param("endDate", "2026-02-27"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPublicCourtListPdf_returns400_whenStartDateInvalidFormat() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .param("courtCentreId", COURT_CENTRE_ID)
                        .param("startDate", "27-02-2026")
                        .param("endDate", END_DATE))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPublicCourtListPdf_returns502_whenServiceThrows() throws Exception {
        when(publicCourtListService.generatePublicCourtListPdf(any(), any(), any()))
                .thenThrow(new PublicCourtListException("Failed to fetch court list"));

        mockMvc.perform(get(BASE_URL)
                        .param("courtCentreId", COURT_CENTRE_ID)
                        .param("startDate", START_DATE)
                        .param("endDate", END_DATE))
                .andExpect(status().isBadGateway());
    }
}
