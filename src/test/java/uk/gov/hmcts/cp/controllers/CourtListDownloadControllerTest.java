package uk.gov.hmcts.cp.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.gov.hmcts.cp.cleanup.CleanupJobService;
import uk.gov.hmcts.cp.services.CourtListPublishStatusService;
import uk.gov.hmcts.cp.services.CourtListTaskTriggerService;
import uk.gov.hmcts.cp.services.courtlistdownload.CourtListDownloadException;
import uk.gov.hmcts.cp.services.courtlistdownload.CourtListDownloadService;
import uk.gov.hmcts.cp.services.courtlistdownload.CourtListFileResult;
import uk.gov.hmcts.cp.services.sjp.SjpCourtListPublishService;

import java.time.LocalDate;
import uk.gov.hmcts.cp.openapi.model.CourtListType;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CourtListDownloadControllerTest {

    private static final String COURT_CENTRE_ID = "f8254db1-1683-483e-afb3-b87fde5a0a26";
    private static final String START_DATE = "2026-02-27";
    private static final String END_DATE = "2026-02-27";
    private static final String DOWNLOAD_URL = "/api/court-list-publish/download";
    private static final String CJSCPPUID_HEADER = "CJSCPPUID";
    private static final String CJSCPPUID_VALUE = "a085e359-6069-4694-8820-7810e7dfe762";
    private static final String DOWNLOAD_ACCEPT = "application/vnd.courtlistpublishing-service.download.get+json";
    private static final byte[] PDF_BYTES = "PDF content".getBytes();
    private static final byte[] WORD_BYTES = "Word content".getBytes();
    private static final String WORD_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private MockMvc mockMvc;

    @Mock
    private CourtListPublishStatusService service;
    @Mock
    private CourtListTaskTriggerService courtListTaskTriggerService;
    @Mock
    private CourtListDownloadService courtListDownloadService;
    @Mock
    private SjpCourtListPublishService sjpCourtListPublishService;
    @Mock
    private CleanupJobService cleanupJobService;

    @BeforeEach
    void setUp() {
        CourtListPublishController controller = new CourtListPublishController(
                service, courtListTaskTriggerService, courtListDownloadService, cleanupJobService, sjpCourtListPublishService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void downloadCourtListReturnsPdfWhenValidQueryParams() throws Exception {
        CourtListFileResult result = new CourtListFileResult(PDF_BYTES, "application/pdf", "CourtList.pdf");
        when(courtListDownloadService.generateCourtListDownload(
                eq(CourtListType.PUBLIC),
                eq(COURT_CENTRE_ID),
                isNull(),
                any(LocalDate.class),
                any(LocalDate.class),
                eq(CJSCPPUID_VALUE)))
                .thenReturn(result);

        mockMvc.perform(get(DOWNLOAD_URL)
                        .header("Accept", DOWNLOAD_ACCEPT)
                        .header(CJSCPPUID_HEADER, CJSCPPUID_VALUE)
                        .param("courtCentreId", COURT_CENTRE_ID)
                        .param("startDate", START_DATE)
                        .param("endDate", END_DATE)
                        .param("courtListType", "PUBLIC"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"CourtList.pdf\""))
                .andExpect(content().bytes(PDF_BYTES));

        verify(courtListDownloadService).generateCourtListDownload(
                eq(CourtListType.PUBLIC), eq(COURT_CENTRE_ID), isNull(), any(LocalDate.class), any(LocalDate.class), eq(CJSCPPUID_VALUE));
    }

    @Test
    void downloadCourtListReturns400WhenCjscppuidHeaderMissing() throws Exception {
        mockMvc.perform(get(DOWNLOAD_URL)
                        .header("Accept", DOWNLOAD_ACCEPT)
                        .param("courtCentreId", COURT_CENTRE_ID)
                        .param("startDate", START_DATE)
                        .param("endDate", END_DATE)
                        .param("courtListType", "PUBLIC"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void downloadCourtListReturns400WhenCourtCentreIdMissing() throws Exception {
        mockMvc.perform(get(DOWNLOAD_URL)
                        .header("Accept", DOWNLOAD_ACCEPT)
                        .header(CJSCPPUID_HEADER, CJSCPPUID_VALUE)
                        .param("startDate", START_DATE)
                        .param("endDate", END_DATE)
                        .param("courtListType", "PUBLIC"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void downloadCourtListReturns400WhenStartDateMissing() throws Exception {
        mockMvc.perform(get(DOWNLOAD_URL)
                        .header("Accept", DOWNLOAD_ACCEPT)
                        .header(CJSCPPUID_HEADER, CJSCPPUID_VALUE)
                        .param("courtCentreId", COURT_CENTRE_ID)
                        .param("endDate", END_DATE)
                        .param("courtListType", "PUBLIC"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void downloadCourtListReturns400WhenEndDateMissing() throws Exception {
        mockMvc.perform(get(DOWNLOAD_URL)
                        .header("Accept", DOWNLOAD_ACCEPT)
                        .header(CJSCPPUID_HEADER, CJSCPPUID_VALUE)
                        .param("courtCentreId", COURT_CENTRE_ID)
                        .param("startDate", START_DATE)
                        .param("courtListType", "PUBLIC"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void downloadCourtListReturns400WhenCourtListTypeMissing() throws Exception {
        mockMvc.perform(get(DOWNLOAD_URL)
                        .header("Accept", DOWNLOAD_ACCEPT)
                        .header(CJSCPPUID_HEADER, CJSCPPUID_VALUE)
                        .param("courtCentreId", COURT_CENTRE_ID)
                        .param("startDate", START_DATE)
                        .param("endDate", END_DATE))
                .andExpect(status().isBadRequest());
    }

    @Test
    void downloadCourtListReturns400WhenCourtListTypeNotSupported() throws Exception {
        mockMvc.perform(get(DOWNLOAD_URL)
                        .header("Accept", DOWNLOAD_ACCEPT)
                        .header(CJSCPPUID_HEADER, CJSCPPUID_VALUE)
                        .param("courtCentreId", COURT_CENTRE_ID)
                        .param("startDate", START_DATE)
                        .param("endDate", END_DATE)
                        .param("courtListType", "STANDARD"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void downloadCourtListReturnsPdfWhenJudge() throws Exception {
        CourtListFileResult result = new CourtListFileResult(PDF_BYTES, "application/pdf", "CourtList.pdf");
        when(courtListDownloadService.generateCourtListDownload(
                eq(CourtListType.JUDGE),
                eq(COURT_CENTRE_ID),
                isNull(),
                any(LocalDate.class),
                any(LocalDate.class),
                eq(CJSCPPUID_VALUE)))
                .thenReturn(result);

        mockMvc.perform(get(DOWNLOAD_URL)
                        .header("Accept", DOWNLOAD_ACCEPT)
                        .header(CJSCPPUID_HEADER, CJSCPPUID_VALUE)
                        .param("courtCentreId", COURT_CENTRE_ID)
                        .param("startDate", START_DATE)
                        .param("endDate", END_DATE)
                        .param("courtListType", "JUDGE"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"CourtList.pdf\""))
                .andExpect(content().bytes(PDF_BYTES));
    }

    @Test
    void downloadCourtListReturns400WhenEndDateBeforeStartDate() throws Exception {
        mockMvc.perform(get(DOWNLOAD_URL)
                        .header("Accept", DOWNLOAD_ACCEPT)
                        .header(CJSCPPUID_HEADER, CJSCPPUID_VALUE)
                        .param("courtCentreId", COURT_CENTRE_ID)
                        .param("startDate", "2026-02-28")
                        .param("endDate", END_DATE)
                        .param("courtListType", "PUBLIC"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void downloadCourtListReturnsWordWhenUshersCrown() throws Exception {
        CourtListFileResult wordResult = new CourtListFileResult(WORD_BYTES, WORD_CONTENT_TYPE, "CourtList.docx");
        when(courtListDownloadService.generateCourtListDownload(
                eq(CourtListType.USHERS_CROWN),
                eq(COURT_CENTRE_ID),
                isNull(),
                any(LocalDate.class),
                any(LocalDate.class),
                eq(CJSCPPUID_VALUE)))
                .thenReturn(wordResult);

        mockMvc.perform(get(DOWNLOAD_URL)
                        .header("Accept", DOWNLOAD_ACCEPT)
                        .header(CJSCPPUID_HEADER, CJSCPPUID_VALUE)
                        .param("courtCentreId", COURT_CENTRE_ID)
                        .param("startDate", START_DATE)
                        .param("endDate", END_DATE)
                        .param("courtListType", "USHERS_CROWN"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(WORD_CONTENT_TYPE))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"CourtList.docx\""))
                .andExpect(content().bytes(WORD_BYTES));

        verify(courtListDownloadService).generateCourtListDownload(
                eq(CourtListType.USHERS_CROWN), eq(COURT_CENTRE_ID), isNull(), any(LocalDate.class), any(LocalDate.class), eq(CJSCPPUID_VALUE));
    }

    @Test
    void downloadCourtListReturnsWordWhenUshersMagistrate() throws Exception {
        CourtListFileResult wordResult = new CourtListFileResult(WORD_BYTES, WORD_CONTENT_TYPE, "CourtList.docx");
        when(courtListDownloadService.generateCourtListDownload(
                eq(CourtListType.USHERS_MAGISTRATE),
                eq(COURT_CENTRE_ID),
                isNull(),
                any(LocalDate.class),
                any(LocalDate.class),
                eq(CJSCPPUID_VALUE)))
                .thenReturn(wordResult);

        mockMvc.perform(get(DOWNLOAD_URL)
                        .header("Accept", DOWNLOAD_ACCEPT)
                        .header(CJSCPPUID_HEADER, CJSCPPUID_VALUE)
                        .param("courtCentreId", COURT_CENTRE_ID)
                        .param("startDate", START_DATE)
                        .param("endDate", END_DATE)
                        .param("courtListType", "USHERS_MAGISTRATE"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(WORD_CONTENT_TYPE))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"CourtList.docx\""))
                .andExpect(content().bytes(WORD_BYTES));
    }

    @Test
    void downloadCourtListReturns502WhenServiceThrows() throws Exception {
        when(courtListDownloadService.generateCourtListDownload(any(CourtListType.class), any(), any(), any(LocalDate.class), any(LocalDate.class), any()))
                .thenThrow(new CourtListDownloadException("Failed to fetch court list"));

        mockMvc.perform(get(DOWNLOAD_URL)
                        .header("Accept", DOWNLOAD_ACCEPT)
                        .header(CJSCPPUID_HEADER, CJSCPPUID_VALUE)
                        .param("courtCentreId", COURT_CENTRE_ID)
                        .param("startDate", START_DATE)
                        .param("endDate", END_DATE)
                        .param("courtListType", "PUBLIC"))
                .andExpect(status().isBadGateway());
    }
}
