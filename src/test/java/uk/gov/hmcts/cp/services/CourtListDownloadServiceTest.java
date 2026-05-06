package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.services.courtlistdownload.CourtListDownloadException;
import uk.gov.hmcts.cp.services.courtlistdownload.CourtListDownloadService;
import uk.gov.hmcts.cp.services.courtlistdownload.CourtListFileResult;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class CourtListDownloadServiceTest {

    private static final String COURT_CENTRE_ID = "f8254db1-1683-483e-afb3-b87fde5a0a26";
    private static final LocalDate START_DATE = LocalDate.of(2026, 2, 27);
    private static final LocalDate END_DATE = LocalDate.of(2026, 2, 27);
    private static final byte[] PDF_BYTES = new byte[]{1, 2, 3};
    private static final byte[] WORD_BYTES = new byte[]{1, 2, 3, 4};
    private static final String TEMPLATE_PUBLIC_COURT_LIST = "PublicCourtList";
    private static final String TEMPLATE_USHERS_CROWN_COURT_LIST = "UshersCrownCourtList";
    private static final String TEMPLATE_USHERS_MAGISTRATE_COURT_LIST = "UshersMagistrateCourtList";
    private static final String KEY_LIST_TYPE = "listType";
    private static final String CJSCPPUID = "a085e359-6069-4694-8820-7810e7dfe762";
    private static final String WORD_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Mock
    private DocumentGeneratorClient documentGeneratorClient;

    @Mock
    private CourtListDataService courtListDataService;

    private CourtListDownloadService service;

    @BeforeEach
    void setUp() {
        service = new CourtListDownloadService(courtListDataService, documentGeneratorClient);
    }

    @Test
    void generateCourtListDownloadReturnsPdfWhenPublic() throws IOException {
        Map<String, Object> payload = Map.of(
                "templateName", TEMPLATE_PUBLIC_COURT_LIST,
                KEY_LIST_TYPE, "public",
                "courtCentreName", "Test Court");
        when(courtListDataService.getCourtListPayloadForDownload(
                eq(CourtListType.PUBLIC), eq(COURT_CENTRE_ID), isNull(), eq(START_DATE), eq(END_DATE), eq(CJSCPPUID))).thenReturn(payload);
        when(documentGeneratorClient.generatePdf(any(), eq(TEMPLATE_PUBLIC_COURT_LIST))).thenReturn(PDF_BYTES);

        CourtListFileResult result = service.generateCourtListDownload(
                CourtListType.PUBLIC, COURT_CENTRE_ID, null, START_DATE, END_DATE, CJSCPPUID);

        assertThat(result.content()).isEqualTo(PDF_BYTES);
        assertThat(result.contentType()).isEqualTo("application/pdf");
        assertThat(result.filename()).isEqualTo("CourtList.pdf");
        verify(documentGeneratorClient).generatePdf(any(), eq(TEMPLATE_PUBLIC_COURT_LIST));
    }

    @Test
    void generateCourtListDownloadUsesDefaultTemplateWhenPayloadHasNoTemplateName() throws IOException {
        Map<String, Object> payload = Map.of(KEY_LIST_TYPE, "public");
        when(courtListDataService.getCourtListPayloadForDownload(
                eq(CourtListType.PUBLIC), eq(COURT_CENTRE_ID), isNull(), eq(START_DATE), eq(END_DATE), eq(CJSCPPUID))).thenReturn(payload);
        when(documentGeneratorClient.generatePdf(any(), eq(TEMPLATE_PUBLIC_COURT_LIST))).thenReturn(PDF_BYTES);

        CourtListFileResult result = service.generateCourtListDownload(
                CourtListType.PUBLIC, COURT_CENTRE_ID, null, START_DATE, END_DATE, CJSCPPUID);

        assertThat(result.content()).isEqualTo(PDF_BYTES);
        verify(documentGeneratorClient).generatePdf(any(), eq(TEMPLATE_PUBLIC_COURT_LIST));
    }

    @Test
    void generateCourtListDownloadThrowsWhenDocumentGeneratorClientThrows() throws IOException {
        Map<String, Object> payload = Map.of("templateName", TEMPLATE_PUBLIC_COURT_LIST);
        when(courtListDataService.getCourtListPayloadForDownload(
                eq(CourtListType.PUBLIC), eq(COURT_CENTRE_ID), isNull(), eq(START_DATE), eq(END_DATE), eq(CJSCPPUID))).thenReturn(payload);
        when(documentGeneratorClient.generatePdf(any(), any())).thenThrow(new IOException("Document generator failed"));

        assertThatThrownBy(() -> service.generateCourtListDownload(
                CourtListType.PUBLIC, COURT_CENTRE_ID, null, START_DATE, END_DATE, CJSCPPUID))
                .isInstanceOf(CourtListDownloadException.class)
                .hasMessageContaining("Document generator failed");
    }

    @Test
    void generateCourtListDownloadThrowsWhenCourtListDataReturnsEmpty() {
        when(courtListDataService.getCourtListPayloadForDownload(
                eq(CourtListType.PUBLIC), eq(COURT_CENTRE_ID), isNull(), eq(START_DATE), eq(END_DATE), eq(CJSCPPUID))).thenReturn(Map.of());

        assertThatThrownBy(() -> service.generateCourtListDownload(
                CourtListType.PUBLIC, COURT_CENTRE_ID, null, START_DATE, END_DATE, CJSCPPUID))
                .isInstanceOf(CourtListDownloadException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void generateCourtListDownloadThrowsWhenCourtListDataReturnsNull() {
        when(courtListDataService.getCourtListPayloadForDownload(
                eq(CourtListType.PUBLIC), eq(COURT_CENTRE_ID), isNull(), eq(START_DATE), eq(END_DATE), eq(CJSCPPUID))).thenReturn(null);

        assertThatThrownBy(() -> service.generateCourtListDownload(
                CourtListType.PUBLIC, COURT_CENTRE_ID, null, START_DATE, END_DATE, CJSCPPUID))
                .isInstanceOf(CourtListDownloadException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void generateCourtListDownloadUsesBenchTemplateWhenCourtListTypeIsBench() throws IOException {
        Map<String, Object> payload = Map.of(KEY_LIST_TYPE, "bench", "courtCentreName", "Test Court");
        when(courtListDataService.getCourtListPayloadForDownload(
                eq(CourtListType.BENCH), eq(COURT_CENTRE_ID), isNull(), eq(START_DATE), eq(END_DATE), eq(CJSCPPUID))).thenReturn(payload);
        when(documentGeneratorClient.generatePdf(any(), eq("BenchCourtList"))).thenReturn(PDF_BYTES);

        CourtListFileResult result = service.generateCourtListDownload(
                CourtListType.BENCH, COURT_CENTRE_ID, null, START_DATE, END_DATE, CJSCPPUID);

        assertThat(result.content()).isEqualTo(PDF_BYTES);
        assertThat(result.contentType()).isEqualTo("application/pdf");
        verify(documentGeneratorClient).generatePdf(any(), eq("BenchCourtList"));
    }

    @Test
    void generateCourtListDownloadReturnsWordWhenUshersCrown() throws IOException {
        Map<String, Object> payload = Map.of(KEY_LIST_TYPE, "ushers_crown", "courtCentreName", "Test Court");
        when(courtListDataService.getCourtListPayloadForDownload(
                eq(CourtListType.USHERS_CROWN), eq(COURT_CENTRE_ID), isNull(), eq(START_DATE), eq(END_DATE), eq(CJSCPPUID))).thenReturn(payload);
        when(documentGeneratorClient.generateWord(any(), eq(TEMPLATE_USHERS_CROWN_COURT_LIST))).thenReturn(WORD_BYTES);

        CourtListFileResult result = service.generateCourtListDownload(
                CourtListType.USHERS_CROWN, COURT_CENTRE_ID, null, START_DATE, END_DATE, CJSCPPUID);

        assertThat(result.content()).isEqualTo(WORD_BYTES);
        assertThat(result.contentType()).isEqualTo(WORD_CONTENT_TYPE);
        assertThat(result.filename()).isEqualTo("CourtList.docx");
        verify(documentGeneratorClient).generateWord(any(), eq(TEMPLATE_USHERS_CROWN_COURT_LIST));
    }

    @Test
    void generateCourtListDownloadReturnsWordWhenUshersMagistrate() throws IOException {
        Map<String, Object> payload = Map.of(KEY_LIST_TYPE, "ushers_magistrate", "courtCentreName", "Test Court");
        when(courtListDataService.getCourtListPayloadForDownload(
                eq(CourtListType.USHERS_MAGISTRATE), eq(COURT_CENTRE_ID), isNull(), eq(START_DATE), eq(END_DATE), eq(CJSCPPUID))).thenReturn(payload);
        when(documentGeneratorClient.generateWord(any(), eq(TEMPLATE_USHERS_MAGISTRATE_COURT_LIST))).thenReturn(WORD_BYTES);

        CourtListFileResult result = service.generateCourtListDownload(
                CourtListType.USHERS_MAGISTRATE, COURT_CENTRE_ID, null, START_DATE, END_DATE, CJSCPPUID);

        assertThat(result.content()).isEqualTo(WORD_BYTES);
        assertThat(result.contentType()).isEqualTo(WORD_CONTENT_TYPE);
        assertThat(result.filename()).isEqualTo("CourtList.docx");
        verify(documentGeneratorClient).generateWord(any(), eq(TEMPLATE_USHERS_MAGISTRATE_COURT_LIST));
    }
}
