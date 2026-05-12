package uk.gov.hmcts.cp.http;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.cp.openapi.model.CourtListType.STANDARD;

import java.io.InputStream;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * STANDARD: CaTH publish, PDF fetched as a pre-rendered binary from progression /courtlist, uploaded as-is.
 * Asserts the downloaded PDF bytes equal the progression stub's bytes (the binary minimal-pdf.pdf).
 * Clears {@code court_list_publish_status} and jobs before each test.
 */
class CourtListStandardPdfContentIntegrationTest extends CourtListIntegrationTestBase {

    private static final String PROGRESSION_PDF_RESOURCE = "wiremock/__files/minimal-pdf.pdf";

    @BeforeEach
    void clearTablesBeforeTest() {
        clearTables();
    }

    @Test
    void publishCourtList_shouldPublishToCaTHAndUploadProgressionBinaryPdfForSTANDARD() throws Exception {
        byte[] expectedPdfBytes = loadResourceBytes(PROGRESSION_PDF_RESOURCE);
        UUID courtListId = publishCourtListExpectingRequestedInDb(STANDARD.toString());
        awaitSuccessfulPdfAndAssertDb(courtListId, STANDARD.toString());
        byte[] downloaded = downloadPdf(courtListId);
        assertThat(downloaded).isNotNull().isEqualTo(expectedPdfBytes);
    }

    private byte[] loadResourceBytes(String resourcePath) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(in).as("resource %s must exist", resourcePath).isNotNull();
            return in.readAllBytes();
        }
    }
}
