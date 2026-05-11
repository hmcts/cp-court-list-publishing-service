package uk.gov.hmcts.cp.services;

import jakarta.json.JsonObject;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourtListDownloadServiceTest {

    private static final String COURT_CENTRE_ID = "f8254db1-1683-483e-afb3-b87fde5a0a26";
    private static final LocalDate START_DATE = LocalDate.of(2026, 2, 27);
    private static final LocalDate END_DATE = LocalDate.of(2026, 2, 27);
    private static final String CJSCPPUID = "a085e359-6069-4694-8820-7810e7dfe762";
    private static final byte[] PDF_BYTES = "%PDF-1.6 fake".getBytes();
    private static final byte[] WORD_BYTES = new byte[]{'P', 'K', 3, 4, 1, 2, 3};
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String WORD_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String PAYLOAD_JSON = "{\"listType\":\"x\",\"hearingDates\":[]}";

    @Mock
    private CourtListDataService courtListDataService;

    @Mock
    private DocumentGeneratorClient documentGeneratorClient;

    private CourtListDownloadService service;

    @BeforeEach
    void setUp() {
        service = new CourtListDownloadService(courtListDataService, documentGeneratorClient);
    }

    @Test
    void generateCourtListDownloadRendersPdfWithPublicCourtListTemplate() throws IOException {
        when(courtListDataService.getCourtListPayloadForDownload(
                eq(CourtListType.PUBLIC), eq(COURT_CENTRE_ID), isNull(), eq(START_DATE), eq(END_DATE), eq(CJSCPPUID)))
                .thenReturn(PAYLOAD_JSON);
        when(documentGeneratorClient.generatePdf(any(JsonObject.class), eq("PublicCourtList")))
                .thenReturn(PDF_BYTES);

        CourtListFileResult result = service.generateCourtListDownload(
                CourtListType.PUBLIC, COURT_CENTRE_ID, null, START_DATE, END_DATE, CJSCPPUID);

        assertThat(result.content()).isEqualTo(PDF_BYTES);
        assertThat(result.contentType()).isEqualTo(PDF_CONTENT_TYPE);
        assertThat(result.filename()).isEqualTo("CourtList.pdf");
        verify(documentGeneratorClient).generatePdf(any(JsonObject.class), eq("PublicCourtList"));
    }

    @Test
    void generateCourtListDownloadRendersPdfWithBenchCourtListTemplate() throws IOException {
        when(courtListDataService.getCourtListPayloadForDownload(
                eq(CourtListType.BENCH), eq(COURT_CENTRE_ID), isNull(), eq(START_DATE), eq(END_DATE), eq(CJSCPPUID)))
                .thenReturn(PAYLOAD_JSON);
        when(documentGeneratorClient.generatePdf(any(JsonObject.class), eq("BenchCourtList")))
                .thenReturn(PDF_BYTES);

        CourtListFileResult result = service.generateCourtListDownload(
                CourtListType.BENCH, COURT_CENTRE_ID, null, START_DATE, END_DATE, CJSCPPUID);

        assertThat(result.filename()).isEqualTo("CourtList.pdf");
        verify(documentGeneratorClient).generatePdf(any(JsonObject.class), eq("BenchCourtList"));
    }

    @Test
    void generateCourtListDownloadRendersPdfWithBenchAndStandardCourtListTemplateForStandard() throws IOException {
        when(courtListDataService.getCourtListPayloadForDownload(
                eq(CourtListType.STANDARD), eq(COURT_CENTRE_ID), isNull(), eq(START_DATE), eq(END_DATE), eq(CJSCPPUID)))
                .thenReturn(PAYLOAD_JSON);
        when(documentGeneratorClient.generatePdf(any(JsonObject.class), eq("BenchAndStandardCourtList")))
                .thenReturn(PDF_BYTES);

        service.generateCourtListDownload(
                CourtListType.STANDARD, COURT_CENTRE_ID, null, START_DATE, END_DATE, CJSCPPUID);

        verify(documentGeneratorClient).generatePdf(any(JsonObject.class), eq("BenchAndStandardCourtList"));
    }

    @Test
    void generateCourtListDownloadRendersPdfWithAlphabeticalTemplate() throws IOException {
        when(courtListDataService.getCourtListPayloadForDownload(
                eq(CourtListType.ALPHABETICAL), eq(COURT_CENTRE_ID), isNull(), eq(START_DATE), eq(END_DATE), eq(CJSCPPUID)))
                .thenReturn(PAYLOAD_JSON);
        when(documentGeneratorClient.generatePdf(any(JsonObject.class), eq("AlphabeticalCourtList")))
                .thenReturn(PDF_BYTES);

        service.generateCourtListDownload(
                CourtListType.ALPHABETICAL, COURT_CENTRE_ID, null, START_DATE, END_DATE, CJSCPPUID);

        verify(documentGeneratorClient).generatePdf(any(JsonObject.class), eq("AlphabeticalCourtList"));
    }

    @Test
    void generateCourtListDownloadRendersPdfWithJudgeListTemplate() throws IOException {
        when(courtListDataService.getCourtListPayloadForDownload(
                eq(CourtListType.JUDGE), eq(COURT_CENTRE_ID), isNull(), eq(START_DATE), eq(END_DATE), eq(CJSCPPUID)))
                .thenReturn(PAYLOAD_JSON);
        when(documentGeneratorClient.generatePdf(any(JsonObject.class), eq("JudgeList")))
                .thenReturn(PDF_BYTES);

        service.generateCourtListDownload(
                CourtListType.JUDGE, COURT_CENTRE_ID, null, START_DATE, END_DATE, CJSCPPUID);

        verify(documentGeneratorClient).generatePdf(any(JsonObject.class), eq("JudgeList"));
    }

    @Test
    void generateCourtListDownloadRendersWordWithUshersCrownCourtListTemplate() throws IOException {
        when(courtListDataService.getCourtListPayloadForDownload(
                eq(CourtListType.USHERS_CROWN), eq(COURT_CENTRE_ID), isNull(), eq(START_DATE), eq(END_DATE), eq(CJSCPPUID)))
                .thenReturn(PAYLOAD_JSON);
        when(documentGeneratorClient.generateWord(any(JsonObject.class), eq("UshersCrownCourtList")))
                .thenReturn(WORD_BYTES);

        CourtListFileResult result = service.generateCourtListDownload(
                CourtListType.USHERS_CROWN, COURT_CENTRE_ID, null, START_DATE, END_DATE, CJSCPPUID);

        assertThat(result.content()).isEqualTo(WORD_BYTES);
        assertThat(result.contentType()).isEqualTo(WORD_CONTENT_TYPE);
        assertThat(result.filename()).isEqualTo("CourtList.docx");
        verify(documentGeneratorClient).generateWord(any(JsonObject.class), eq("UshersCrownCourtList"));
    }

    @Test
    void generateCourtListDownloadRendersWordWithUshersMagistrateCourtListTemplate() throws IOException {
        when(courtListDataService.getCourtListPayloadForDownload(
                eq(CourtListType.USHERS_MAGISTRATE), eq(COURT_CENTRE_ID), isNull(), eq(START_DATE), eq(END_DATE), eq(CJSCPPUID)))
                .thenReturn(PAYLOAD_JSON);
        when(documentGeneratorClient.generateWord(any(JsonObject.class), eq("UshersMagistrateCourtList")))
                .thenReturn(WORD_BYTES);

        CourtListFileResult result = service.generateCourtListDownload(
                CourtListType.USHERS_MAGISTRATE, COURT_CENTRE_ID, null, START_DATE, END_DATE, CJSCPPUID);

        assertThat(result.filename()).isEqualTo("CourtList.docx");
        assertThat(result.contentType()).isEqualTo(WORD_CONTENT_TYPE);
        verify(documentGeneratorClient).generateWord(any(JsonObject.class), eq("UshersMagistrateCourtList"));
    }

    @Test
    void generateCourtListDownloadForwardsCourtRoomIdToDataService() throws IOException {
        String courtRoomId = "4294a92c-8827-3296-be53-c74b7e9e31d8";
        when(courtListDataService.getCourtListPayloadForDownload(
                eq(CourtListType.ALPHABETICAL), eq(COURT_CENTRE_ID), eq(courtRoomId), eq(START_DATE), eq(END_DATE), eq(CJSCPPUID)))
                .thenReturn(PAYLOAD_JSON);
        when(documentGeneratorClient.generatePdf(any(JsonObject.class), eq("AlphabeticalCourtList")))
                .thenReturn(PDF_BYTES);

        service.generateCourtListDownload(
                CourtListType.ALPHABETICAL, COURT_CENTRE_ID, courtRoomId, START_DATE, END_DATE, CJSCPPUID);

        verify(courtListDataService).getCourtListPayloadForDownload(
                eq(CourtListType.ALPHABETICAL), eq(COURT_CENTRE_ID), eq(courtRoomId), eq(START_DATE), eq(END_DATE), eq(CJSCPPUID));
    }

    @Test
    void generateCourtListDownloadIgnoresTemplateNameInPayload() throws IOException {
        // Even if listing slips a different templateName into the payload, publishing-service
        // owns the template choice and uses its own constant.
        String payloadWithUnknownTemplateName = "{\"listType\":\"standard\",\"templateName\":\"SomeListingOnlyName\"}";
        when(courtListDataService.getCourtListPayloadForDownload(
                eq(CourtListType.STANDARD), eq(COURT_CENTRE_ID), isNull(), eq(START_DATE), eq(END_DATE), eq(CJSCPPUID)))
                .thenReturn(payloadWithUnknownTemplateName);
        when(documentGeneratorClient.generatePdf(any(JsonObject.class), eq("BenchAndStandardCourtList")))
                .thenReturn(PDF_BYTES);

        service.generateCourtListDownload(
                CourtListType.STANDARD, COURT_CENTRE_ID, null, START_DATE, END_DATE, CJSCPPUID);

        verify(documentGeneratorClient).generatePdf(any(JsonObject.class), eq("BenchAndStandardCourtList"));
    }

    @Test
    void generateCourtListDownloadRejectsUnsupportedTypeBeforeCallingListing() {
        assertThatThrownBy(() -> service.generateCourtListDownload(
                CourtListType.PRISON, COURT_CENTRE_ID, null, START_DATE, END_DATE, CJSCPPUID))
                .isInstanceOf(CourtListDownloadException.class)
                .hasMessageContaining("Unsupported court list type for download");
        // Listing must not be called for an unsupported type — fail-fast on the template lookup.
        org.mockito.Mockito.verifyNoInteractions(courtListDataService);
        org.mockito.Mockito.verifyNoInteractions(documentGeneratorClient);
    }

    @Test
    void generateCourtListDownloadThrowsWhenDocumentGeneratorFails() throws IOException {
        when(courtListDataService.getCourtListPayloadForDownload(
                eq(CourtListType.STANDARD), eq(COURT_CENTRE_ID), isNull(), eq(START_DATE), eq(END_DATE), eq(CJSCPPUID)))
                .thenReturn(PAYLOAD_JSON);
        when(documentGeneratorClient.generatePdf(any(JsonObject.class), eq("BenchAndStandardCourtList")))
                .thenThrow(new IOException("docgen down"));

        assertThatThrownBy(() -> service.generateCourtListDownload(
                CourtListType.STANDARD, COURT_CENTRE_ID, null, START_DATE, END_DATE, CJSCPPUID))
                .isInstanceOf(CourtListDownloadException.class)
                .hasMessageContaining("Failed to render court list document");
    }

    @Test
    void generateCourtListDownloadThrowsWhenPayloadJsonInvalid() {
        when(courtListDataService.getCourtListPayloadForDownload(
                eq(CourtListType.STANDARD), eq(COURT_CENTRE_ID), isNull(), eq(START_DATE), eq(END_DATE), eq(CJSCPPUID)))
                .thenReturn("not valid json");

        assertThatThrownBy(() -> service.generateCourtListDownload(
                CourtListType.STANDARD, COURT_CENTRE_ID, null, START_DATE, END_DATE, CJSCPPUID))
                .isInstanceOf(CourtListDownloadException.class)
                .hasMessageContaining("Failed to parse court list payload JSON");
    }
}
