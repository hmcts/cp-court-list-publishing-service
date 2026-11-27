package uk.gov.hmcts.cp.services.crown;

import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.services.CourtListDataService;
import uk.gov.hmcts.cp.services.DocumentGeneratorClient;
import uk.gov.hmcts.cp.services.PdfGenerationService;

import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrownListPdfGeneratorTest {

    private static final UUID COURT_LIST_ID = UUID.fromString("c1000000-0000-0000-0000-000000000001");
    private static final UUID COURT_CENTRE_ID = UUID.fromString("cc000000-0000-0000-0000-000000000001");
    private static final LocalDate MONDAY = LocalDate.of(2026, 6, 8);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 6, 14);
    private static final byte[] PDF = {'%', 'P', 'D', 'F'};

    @Mock
    private CourtListDataService courtListDataService;
    @Mock
    private DocumentGeneratorClient documentGeneratorClient;
    @Mock
    private PdfGenerationService pdfGenerationService;

    @InjectMocks
    private CrownListPdfGenerator generator;

    @Test
    void everyCrownListHasAPdf() {
        assertThat(CrownListType.values()).allMatch(CrownListPdfGenerator::hasPdf);
    }

    @ParameterizedTest
    @CsvSource({
            "DAILY, false, CrownDailyList",
            "DAILY, true,  CrownDailyListWelsh",
            "FIRM,  false, CrownFirmList",
            "FIRM,  true,  CrownFirmListWelsh",
            // no warned list template yet: WARN uses the firm list template
            "WARNED, false, CrownFirmList",
            "WARNED, true,  CrownFirmListWelsh"})
    void rendersTheListsTemplateInItsLanguageAndUploadsThePdf(CrownListType type, boolean welsh, String template)
            throws IOException {
        CrownPublishRequest request = request(type);
        when(courtListDataService.getCrownCourtDailyListPayload(type.courtListType(), COURT_CENTRE_ID.toString(), null,
                request.startDate(), request.endDate(), "user-id", false))
                .thenReturn("{\"courtLists\":[],\"isWelsh\":" + welsh + "}");
        when(documentGeneratorClient.generatePdf(any(JsonObject.class), eq(template))).thenReturn(PDF);
        when(pdfGenerationService.uploadPdfBytes(PDF, COURT_LIST_ID)).thenReturn(COURT_LIST_ID);

        assertThat(generator.generateAndUpload(request)).isEqualTo(COURT_LIST_ID);

        ArgumentCaptor<JsonObject> payload = ArgumentCaptor.forClass(JsonObject.class);
        verify(documentGeneratorClient).generatePdf(payload.capture(), eq(template));
        assertThat(payload.getValue().containsKey("courtLists")).isTrue();
    }

    @Test
    void usesTheEnglishTemplateWhenThePayloadDoesNotSayWelsh() throws IOException {
        when(courtListDataService.getCrownCourtDailyListPayload(any(), anyString(), any(), any(), any(), any(), eq(false)))
                .thenReturn("{\"courtLists\":[]}");
        when(documentGeneratorClient.generatePdf(any(JsonObject.class), eq("CrownDailyList"))).thenReturn(PDF);

        generator.generateAndUpload(request(CrownListType.DAILY));

        verify(pdfGenerationService).uploadPdfBytes(PDF, COURT_LIST_ID);
    }

    @Test
    void failsWhenTheDocumentGeneratorFailsAndUploadsNothing() throws IOException {
        when(courtListDataService.getCrownCourtDailyListPayload(any(), anyString(), any(), any(), any(), any(), eq(false)))
                .thenReturn("{\"courtLists\":[]}");
        when(documentGeneratorClient.generatePdf(any(JsonObject.class), anyString()))
                .thenThrow(new IOException("document generator returned 500"));

        assertThatThrownBy(() -> generator.generateAndUpload(request(CrownListType.FIRM)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CrownFirmList")
                .hasMessageContaining("document generator returned 500");
        verifyNoInteractions(pdfGenerationService);
    }

    @Test
    void failsWhenListingReturnsSomethingOtherThanAJsonObject() {
        when(courtListDataService.getCrownCourtDailyListPayload(any(), anyString(), any(), any(), any(), any(), eq(false)))
                .thenReturn("not json");

        assertThatThrownBy(() -> generator.generateAndUpload(request(CrownListType.DAILY)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a JSON object");
        verifyNoInteractions(documentGeneratorClient, pdfGenerationService);
    }

    private static CrownPublishRequest request(CrownListType type) {
        LocalDate endDate = type == CrownListType.DAILY ? MONDAY : SUNDAY;
        return new CrownPublishRequest(COURT_LIST_ID, COURT_CENTRE_ID, type, MONDAY, endDate, "user-id", "001", null);
    }
}
