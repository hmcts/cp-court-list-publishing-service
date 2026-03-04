package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.services.courtlistdownload.CourtListDownloadException;
import uk.gov.hmcts.cp.services.courtlistdownload.CourtListDownloadService;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class CourtListDownloadServiceTest {

    private static final String COURT_CENTRE_ID = "f8254db1-1683-483e-afb3-b87fde5a0a26";
    private static final LocalDate START_DATE = LocalDate.of(2026, 2, 27);
    private static final LocalDate END_DATE = LocalDate.of(2026, 2, 27);
    private static final byte[] PDF_BYTES = new byte[]{1, 2, 3};
    private static final String TEMPLATE_PUBLIC_COURT_LIST = "PublicCourtList";

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
    void generatePublicCourtListPdf_returnsPdf_whenCourtListDataSucceedsAndDocGenReturnsPdf() throws IOException {
        Map<String, Object> payload = Map.of(
                "templateName", TEMPLATE_PUBLIC_COURT_LIST,
                "listType", "public",
                "courtCentreName", "Test Court");
        when(courtListDataService.getPublicCourtListPayload(COURT_CENTRE_ID, START_DATE, END_DATE)).thenReturn(payload);
        when(documentGeneratorClient.generatePdf(any(), eq(TEMPLATE_PUBLIC_COURT_LIST))).thenReturn(PDF_BYTES);

        byte[] result = service.generatePublicCourtListPdf(COURT_CENTRE_ID, START_DATE, END_DATE);

        assertThat(result).isEqualTo(PDF_BYTES);
        verify(courtListDataService).getPublicCourtListPayload(COURT_CENTRE_ID, START_DATE, END_DATE);
        verify(documentGeneratorClient).generatePdf(any(), eq(TEMPLATE_PUBLIC_COURT_LIST));
    }

    @Test
    void generatePublicCourtListPdf_usesDefaultTemplate_whenPayloadHasNoTemplateName() throws IOException {
        Map<String, Object> payload = Map.of("listType", "public");
        when(courtListDataService.getPublicCourtListPayload(COURT_CENTRE_ID, START_DATE, END_DATE)).thenReturn(payload);
        when(documentGeneratorClient.generatePdf(any(), eq(TEMPLATE_PUBLIC_COURT_LIST))).thenReturn(PDF_BYTES);

        byte[] result = service.generatePublicCourtListPdf(COURT_CENTRE_ID, START_DATE, END_DATE);

        assertThat(result).isEqualTo(PDF_BYTES);
        verify(documentGeneratorClient).generatePdf(any(), eq(TEMPLATE_PUBLIC_COURT_LIST));
    }

    @Test
    void generatePublicCourtListPdf_throws_whenDocumentGeneratorClientThrows() throws IOException {
        Map<String, Object> payload = Map.of("templateName", TEMPLATE_PUBLIC_COURT_LIST);
        when(courtListDataService.getPublicCourtListPayload(COURT_CENTRE_ID, START_DATE, END_DATE)).thenReturn(payload);
        when(documentGeneratorClient.generatePdf(any(), any())).thenThrow(new IOException("Document generator failed"));

        assertThatThrownBy(() -> service.generatePublicCourtListPdf(COURT_CENTRE_ID, START_DATE, END_DATE))
                .isInstanceOf(CourtListDownloadException.class)
                .hasMessageContaining("Document generator failed");
    }

    @Test
    void generatePublicCourtListPdf_throws_whenCourtListDataReturnsEmpty() {
        when(courtListDataService.getPublicCourtListPayload(COURT_CENTRE_ID, START_DATE, END_DATE)).thenReturn(Map.of());

        assertThatThrownBy(() -> service.generatePublicCourtListPdf(COURT_CENTRE_ID, START_DATE, END_DATE))
                .isInstanceOf(CourtListDownloadException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void generatePublicCourtListPdf_throws_whenCourtListDataReturnsNull() {
        when(courtListDataService.getPublicCourtListPayload(COURT_CENTRE_ID, START_DATE, END_DATE)).thenReturn(null);

        assertThatThrownBy(() -> service.generatePublicCourtListPdf(COURT_CENTRE_ID, START_DATE, END_DATE))
                .isInstanceOf(CourtListDownloadException.class)
                .hasMessageContaining("empty");
    }
}
