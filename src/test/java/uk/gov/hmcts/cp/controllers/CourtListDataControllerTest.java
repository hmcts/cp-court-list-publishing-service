package uk.gov.hmcts.cp.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.services.CourtListDataService;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CourtListDataControllerTest {

    private static final String COURTLISTDATA_URL = "/api/court-list-publish/courtlistdata";
    private static final String COURT_LIST_DATA_MEDIA_TYPE = "application/vnd.progression.search.court.list.data+json";

    private MockMvc mockMvc;

    @Mock
    private CourtListDataService courtListDataService;

    @InjectMocks
    private CourtListDataController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getCourtlistData_returnsCourtListDataFromListingAndReferenceData() throws Exception {
        String payload = "{\"listType\":\"standard\",\"courtCentreName\":\"Test Court\",\"templateName\":\"PublicCourtList\"}";
        when(courtListDataService.getCourtListData(
                eq(CourtListType.STANDARD),
                eq("f8254db1-1683-483e-afb3-b87fde5a0a26"),
                isNull(),
                eq("2024-01-15"),
                eq("2024-01-15"),
                eq(false)))
                .thenReturn(payload);

        mockMvc.perform(get(COURTLISTDATA_URL)
                        .param("courtCentreId", "f8254db1-1683-483e-afb3-b87fde5a0a26")
                        .param("listId", "STANDARD")
                        .param("startDate", "2024-01-15")
                        .param("endDate", "2024-01-15"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(COURT_LIST_DATA_MEDIA_TYPE))
                .andExpect(content().json(payload));

        verify(courtListDataService).getCourtListData(
                eq(CourtListType.STANDARD),
                eq("f8254db1-1683-483e-afb3-b87fde5a0a26"),
                isNull(),
                eq("2024-01-15"),
                eq("2024-01-15"),
                eq(false));
    }

    @Test
    void getCourtlistData_returns403WhenListIdIsPrison() throws Exception {
        mockMvc.perform(get(COURTLISTDATA_URL)
                        .param("courtCentreId", "f8254db1-1683-483e-afb3-b87fde5a0a26")
                        .param("listId", "PRISON")
                        .param("startDate", "2024-01-15")
                        .param("endDate", "2024-01-15"))
                .andExpect(status().isForbidden());
    }
}
