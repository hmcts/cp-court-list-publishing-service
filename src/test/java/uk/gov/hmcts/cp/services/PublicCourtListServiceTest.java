package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.services.publiccourtlist.PublicCourtListException;
import uk.gov.hmcts.cp.services.publiccourtlist.PublicCourtListService;

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
class PublicCourtListServiceTest {

    private static final String COURT_CENTRE_ID = "f8254db1-1683-483e-afb3-b87fde5a0a26";
    private static final LocalDate START_DATE = LocalDate.of(2026, 2, 27);
    private static final LocalDate END_DATE = LocalDate.of(2026, 2, 27);
    private static final byte[] PDF_BYTES = new byte[]{1, 2, 3};
    private static final String TEMPLATE_PUBLIC_COURT_LIST = "PublicCourtList";

    @Mock
    private CourtListDataService courtListDataService;

    @Mock
    private DocumentGeneratorClient documentGeneratorClient;

    private PublicCourtListService service;

    @BeforeEach
    void setUp() {
        service = new PublicCourtListService(courtListDataService, documentGeneratorClient);
    }

    @Test
    void generatePublicCourtListPdf_returnsPdf_whenCourtListDataSucceedsAndDocGenReturnsPdf() throws IOException {
        Map<String, Object> payload = Map.of(
                "templateName", "PublicCourtList",
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
        Map<String, Object> payload = Map.of("templateName", "PublicCourtList");
        when(courtListDataService.getPublicCourtListPayload(COURT_CENTRE_ID, START_DATE, END_DATE)).thenReturn(payload);
        when(documentGeneratorClient.generatePdf(any(), any())).thenThrow(new IOException("Document generator failed"));

        assertThatThrownBy(() -> service.generatePublicCourtListPdf(COURT_CENTRE_ID, START_DATE, END_DATE))
                .isInstanceOf(PublicCourtListException.class)
                .hasMessageContaining("Document generator failed");
    }

    @Test
    void generatePublicCourtListPdf_throws_whenCourtListDataReturnsEmpty() {
        when(courtListDataService.getPublicCourtListPayload(COURT_CENTRE_ID, START_DATE, END_DATE)).thenReturn(Map.of());

        assertThatThrownBy(() -> service.generatePublicCourtListPdf(COURT_CENTRE_ID, START_DATE, END_DATE))
                .isInstanceOf(PublicCourtListException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void generatePublicCourtListPdf_throws_whenCourtListDataReturnsNull() {
        when(courtListDataService.getPublicCourtListPayload(COURT_CENTRE_ID, START_DATE, END_DATE)).thenReturn(null);

        assertThatThrownBy(() -> service.generatePublicCourtListPdf(COURT_CENTRE_ID, START_DATE, END_DATE))
                .isInstanceOf(PublicCourtListException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void generatePublicCourtListPdf_throws_whenCourtListDataServiceThrowsPublicCourtListException() {
        PublicCourtListException cause = new PublicCourtListException("Failed to fetch court list: connection refused");
        when(courtListDataService.getPublicCourtListPayload(COURT_CENTRE_ID, START_DATE, END_DATE))
                .thenThrow(cause);

        assertThatThrownBy(() -> service.generatePublicCourtListPdf(COURT_CENTRE_ID, START_DATE, END_DATE))
                .isInstanceOf(PublicCourtListException.class)
                .hasMessageContaining("Court list data failed")
                .hasMessageContaining(COURT_CENTRE_ID)
                .hasMessageContaining("connection refused")
                .hasCause(cause);
    }

    @Test
    void generatePublicCourtListPdf_throws_whenCourtListDataServiceThrowsOtherRuntimeException() {
        when(courtListDataService.getPublicCourtListPayload(COURT_CENTRE_ID, START_DATE, END_DATE))
                .thenThrow(new IllegalStateException("API unavailable"));

        assertThatThrownBy(() -> service.generatePublicCourtListPdf(COURT_CENTRE_ID, START_DATE, END_DATE))
                .isInstanceOf(PublicCourtListException.class)
                .hasMessageContaining("Court list data API call failed")
                .hasMessageContaining("API unavailable")
                .hasCauseInstanceOf(IllegalStateException.class);
    }
}
