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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private static final byte[] PDF_BYTES = new byte[]{1, 2, 3};
    private static final byte[] WORD_BYTES = new byte[]{1, 2, 3, 4};
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String WORD_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Mock
    private CourtListDataService courtListDataService;

    private CourtListDownloadService service;

    @BeforeEach
    void setUp() {
        service = new CourtListDownloadService(courtListDataService);
    }

    @Test
    void generateCourtListDownloadReturnsPdfResultFromListing() {
        CourtListFileResult listingResult = new CourtListFileResult(PDF_BYTES, PDF_CONTENT_TYPE, "CourtList.pdf");
        when(courtListDataService.getCourtListDocumentForDownload(
                eq(CourtListType.PUBLIC), eq(COURT_CENTRE_ID), isNull(), eq(START_DATE), eq(END_DATE), eq(CJSCPPUID)))
                .thenReturn(listingResult);

        CourtListFileResult result = service.generateCourtListDownload(
                CourtListType.PUBLIC, COURT_CENTRE_ID, null, START_DATE, END_DATE, CJSCPPUID);

        assertThat(result.content()).isEqualTo(PDF_BYTES);
        assertThat(result.contentType()).isEqualTo(PDF_CONTENT_TYPE);
        assertThat(result.filename()).isEqualTo("CourtList.pdf");
        verify(courtListDataService).getCourtListDocumentForDownload(
                eq(CourtListType.PUBLIC), eq(COURT_CENTRE_ID), isNull(), eq(START_DATE), eq(END_DATE), eq(CJSCPPUID));
    }

    @Test
    void generateCourtListDownloadForwardsCourtRoomIdWhenPresent() {
        String courtRoomId = "4294a92c-8827-3296-be53-c74b7e9e31d8";
        CourtListFileResult listingResult = new CourtListFileResult(PDF_BYTES, PDF_CONTENT_TYPE, "CourtList.pdf");
        when(courtListDataService.getCourtListDocumentForDownload(
                eq(CourtListType.ALPHABETICAL), eq(COURT_CENTRE_ID), eq(courtRoomId), eq(START_DATE), eq(END_DATE), eq(CJSCPPUID)))
                .thenReturn(listingResult);

        service.generateCourtListDownload(
                CourtListType.ALPHABETICAL, COURT_CENTRE_ID, courtRoomId, START_DATE, END_DATE, CJSCPPUID);

        verify(courtListDataService).getCourtListDocumentForDownload(
                eq(CourtListType.ALPHABETICAL), eq(COURT_CENTRE_ID), eq(courtRoomId), eq(START_DATE), eq(END_DATE), eq(CJSCPPUID));
    }

    @Test
    void generateCourtListDownloadReturnsWordResultForUshersTypes() {
        CourtListFileResult listingResult = new CourtListFileResult(WORD_BYTES, WORD_CONTENT_TYPE, "CourtList.docx");
        when(courtListDataService.getCourtListDocumentForDownload(
                eq(CourtListType.USHERS_CROWN), eq(COURT_CENTRE_ID), isNull(), eq(START_DATE), eq(END_DATE), eq(CJSCPPUID)))
                .thenReturn(listingResult);

        CourtListFileResult result = service.generateCourtListDownload(
                CourtListType.USHERS_CROWN, COURT_CENTRE_ID, null, START_DATE, END_DATE, CJSCPPUID);

        assertThat(result.content()).isEqualTo(WORD_BYTES);
        assertThat(result.contentType()).isEqualTo(WORD_CONTENT_TYPE);
        assertThat(result.filename()).isEqualTo("CourtList.docx");
    }

    @Test
    void generateCourtListDownloadPropagatesUnsupportedTypeException() {
        when(courtListDataService.getCourtListDocumentForDownload(
                eq(CourtListType.STANDARD), eq(COURT_CENTRE_ID), isNull(), eq(START_DATE), eq(END_DATE), eq(CJSCPPUID)))
                .thenThrow(new CourtListDownloadException("Unsupported court list type for download: STANDARD"));

        assertThatThrownBy(() -> service.generateCourtListDownload(
                CourtListType.STANDARD, COURT_CENTRE_ID, null, START_DATE, END_DATE, CJSCPPUID))
                .isInstanceOf(CourtListDownloadException.class)
                .hasMessageContaining("Unsupported court list type for download");
    }
}
