package uk.gov.hmcts.cp.http;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.cp.openapi.model.CourtListType.STANDARD;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Integration test for STANDARD: CaTH publish, PDF generation, and PDF text vs expected file.
 * Clears court_list_publish_status and jobs before the test.
 */
@Slf4j
class CourtListStandardPdfContentIntegrationTest extends CourtListIntegrationTestBase {

    private static final String EXPECTED_PDF_TXT = "wiremock/__files/expected-standard-pdf-content.txt";

    @BeforeEach
    void clearTablesBeforeTest() {
        clearTables();
    }

    @Test
    void publishCourtList_shouldPublishToCaTHAndGeneratePdfForSTANDARD_andPdfTextMatchesExpectedContent() throws Exception {
        String expectedContent = loadExpectedText(EXPECTED_PDF_TXT);
        DocumentGeneratorStub.stubDocumentCreate(expectedContent);
        try {
            UUID courtCentreId = UUID.randomUUID();
            String requestJson = createPublishRequestJson(courtCentreId, STANDARD.toString());

            ResponseEntity<String> publishResponse = postPublishRequest(requestJson);
            assertThat(publishResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = parseResponse(publishResponse);
            UUID courtListId = UUID.fromString(body.get("courtListId").asText());
            assertThat(body.get("publishStatus").asText()).isEqualTo("REQUESTED");

            try (Connection c = connection()) {
                assertJobsTableHasRowForCourtListId(c, courtListId);
                assertPublishStatusRow(c, courtListId, "REQUESTED", "REQUESTED", null);
                assertCourtListType(c, courtListId, "STANDARD");
            }

            waitForPDFGenerationFileCompletion(courtListId, FILE_COMPLETION_TIMEOUT_MS);

            ResponseEntity<String> statusResponse = getStatusRequest(courtListId);
            assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode status = parseResponse(statusResponse);
            assertThat(status.get("publishStatus").asText()).isEqualTo("SUCCESSFUL");
            assertThat(status.get("fileStatus").asText()).isEqualTo("SUCCESSFUL");
            assertThat(status.get("fileId").asText()).isEqualTo(courtListId.toString());

            try (Connection c = connection()) {
                assertPublishStatusRow(c, courtListId, "SUCCESSFUL", "SUCCESSFUL", courtListId);
                assertCourtListType(c, courtListId, "STANDARD");
            }

            byte[] downloaded = downloadPdf(courtListId);
            assertThat(downloaded).isNotNull().isNotEmpty();
            assertThat(downloaded).isEqualTo(expectedContent.getBytes(StandardCharsets.UTF_8));
        } finally {
            AbstractTest.resetWireMock();
        }
    }
}
