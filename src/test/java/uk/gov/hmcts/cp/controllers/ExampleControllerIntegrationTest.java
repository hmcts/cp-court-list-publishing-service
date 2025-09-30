package uk.gov.hmcts.cp.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.cp.NonTracingIntegrationTestSetup;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@AutoConfigureMockMvc
@Slf4j
class ExampleControllerIntegrationTest extends NonTracingIntegrationTestSetup {
    @Resource
    private MockMvc mockMvc;

    @Test
    @SuppressWarnings("PMD.UnitTestShouldIncludeAssert")
    void shouldReturnOkWhenValidUrnIsProvided() throws Exception {
        final String caseUrn = "test-case-urn";
        mockMvc.perform(get("/case/{case_urn}/courtschedule", caseUrn)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    // You may need to adjust this depending on the actual fields in CourtScheduleResponse
                    final String responseBody = result.getResponse().getContentAsString();
                    log.info("Response: {}", responseBody);
                    final JsonNode jsonBody = new ObjectMapper().readTree(result.getResponse().getContentAsString());


                    assertEquals("courtSchedule", jsonBody.fieldNames().next());
                    final JsonNode courtSchedule = jsonBody.get("courtSchedule");
                    assertTrue(courtSchedule.isArray());
                    assertEquals(1, courtSchedule.size());

                    final JsonNode hearings = courtSchedule.get(0).get("hearings");
                    assertTrue(hearings.isArray());
                    assertEquals(1, hearings.size());

                    final JsonNode hearing = hearings.get(0);
                    final UUID hearingId = UUID.fromString(hearing.get("hearingId").asText());
                    assertNotNull(hearingId);
                    assertEquals("Requires interpreter", hearing.get("listNote").asText());
                    assertEquals("Sentencing for theft case", hearing.get("hearingDescription").asText());
                    assertEquals("Trial", hearing.get("hearingType").asText());

                    final JsonNode courtSittings = hearing.get("courtSittings");
                    assertTrue(courtSittings.isArray());
                    assertEquals(1, courtSittings.size());

                    final JsonNode sitting = courtSittings.get(0);
                    assertEquals("Central Criminal Court", sitting.get("courtHouse").asText());
                    assertNotNull(sitting.get("sittingStart").asText());
                    assertNotNull(sitting.get("sittingEnd").asText());
                    final UUID judiciaryId = UUID.fromString(sitting.get("judiciaryId").asText());
                    assertNotNull(judiciaryId);
                    log.info("Response Object: {}", jsonBody);
                });
    }
}