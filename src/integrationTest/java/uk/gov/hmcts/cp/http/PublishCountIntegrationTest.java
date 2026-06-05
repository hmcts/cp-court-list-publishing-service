package uk.gov.hmcts.cp.http;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.cp.openapi.model.CourtListType.ONLINE_PUBLIC;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public class PublishCountIntegrationTest extends CourtListIntegrationTestBase {

    private static final long WIREMOCK_STUB_REGISTER_DELAY_MS = 500;

    @BeforeEach
    void setUp() {
        clearTables();
        AbstractTest.resetWireMock();
    }

    @Test
    void publishCount_shouldBeOne_afterFirstSuccessfulPublish() throws Exception {
        UUID courtListId = publishCourtListExpectingRequestedInDb(ONLINE_PUBLIC.toString());
        waitForPDFGenerationFileCompletion(courtListId, TASK_TIMEOUT_MS);

        assertPublishCount(courtListId, 1);
    }

    @Test
    void publishCount_shouldIncrement_onEachSuccessfulRepublish() throws Exception {
        UUID courtCentreId = UUID.randomUUID();

        // First publish — new row, count goes from 0 → 1
        ResponseEntity<String> first = postPublishRequest(createPublishRequestJson(courtCentreId, ONLINE_PUBLIC.toString()));
        UUID courtListId = UUID.fromString(parseResponse(first).get("courtListId").asText());
        waitForPDFGenerationFileCompletion(courtListId, TASK_TIMEOUT_MS);
        assertPublishCount(courtListId, 1);

        // Re-publish same courtCentreId + date + type: createOrUpdate reuses the same row
        ResponseEntity<String> second = postPublishRequest(createPublishRequestJson(courtCentreId, ONLINE_PUBLIC.toString()));
        UUID sameCourtListId = UUID.fromString(parseResponse(second).get("courtListId").asText());
        assertThat(sameCourtListId).isEqualTo(courtListId);
        waitForPDFGenerationFileCompletion(courtListId, TASK_TIMEOUT_MS);

        assertPublishCount(courtListId, 2);
    }

    @Test
    void publishCount_shouldRemainZero_whenFileGenerationFails() throws Exception {
        addDocumentGeneratorFailureStub();
        try {
            UUID courtListId = publishCourtListExpectingRequestedInDb(ONLINE_PUBLIC.toString());
            waitForPDFGenerationFileCompletion(courtListId, TASK_TIMEOUT_MS);

            assertPublishCount(courtListId, 0);
        } finally {
            AbstractTest.resetWireMock();
        }
    }

    @Test
    void publishCount_shouldStillBeOne_whenCathPublishingFails() throws Exception {
        // publishCount is incremented on file upload success, before CaTH is called,
        // so a CaTH failure still results in count = 1.
        addCathFailureStub();
        try {
            UUID courtListId = publishCourtListExpectingRequestedInDb(ONLINE_PUBLIC.toString());
            waitForTaskCompletion(courtListId, TASK_TIMEOUT_MS);

            assertPublishCount(courtListId, 1);
        } finally {
            AbstractTest.resetWireMock();
        }
    }

    private void assertPublishCount(UUID courtListId, int expected) throws SQLException {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT publish_count FROM court_list_publish_status WHERE court_list_id = ?")) {
            ps.setObject(1, courtListId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("Row exists for courtListId %s", courtListId).isTrue();
                assertThat(rs.getInt("publish_count")).isEqualTo(expected);
            }
        }
    }

    private void addDocumentGeneratorFailureStub() throws Exception {
        String mappingJson = """
            {
              "request": {
                "method": "POST",
                "urlPathPattern": "/systemdocgenerator-command-api/command/api/rest/systemdocgenerator/render"
              },
              "response": {
                "status": 500,
                "headers": {"Content-Type": "application/json"},
                "body": "{\\"error\\": \\"PDF generation failed\\"}"
              },
              "priority": 0
            }
            """;
        postWireMockMapping(mappingJson, "document-generator failure");
    }

    private void addCathFailureStub() throws Exception {
        String mappingJson = """
            {
              "request": {
                "method": "POST",
                "urlPath": "%s"
              },
              "response": {
                "status": 500,
                "headers": {"Content-Type": "application/json"},
                "body": "{\\"error\\": \\"Internal Server Error\\"}"
              },
              "priority": 0
            }
            """.formatted(CATH_PUBLICATION_URL_PATH);
        postWireMockMapping(mappingJson, "CaTH failure");
    }

    private void postWireMockMapping(String mappingJson, String label) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = http.exchange(
                WIREMOCK_ADMIN_MAPPINGS,
                HttpMethod.POST,
                new HttpEntity<>(mappingJson, headers),
                String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("WireMock mapping failed (" + label + "): " + response.getStatusCode());
        }
        Thread.sleep(WIREMOCK_STUB_REGISTER_DELAY_MS);
    }
}
