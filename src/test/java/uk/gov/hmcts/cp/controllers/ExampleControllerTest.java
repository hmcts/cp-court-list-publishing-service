package uk.gov.hmcts.cp.controllers;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.openapi.model.*;
import uk.gov.hmcts.cp.repositories.ExampleInMemoryStubRepositoryImpl;
import uk.gov.hmcts.cp.services.ExampleService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class ExampleControllerTest {

    private ExampleController exampleController;

    @BeforeEach
    void setUp() {
        final ExampleService exampleService = new ExampleService(new ExampleInMemoryStubRepositoryImpl());
        exampleController = new ExampleController(exampleService);
    }

    @Test
    void get_judge_by_id_should_return_judges_with_ok_status() {
        final UUID caseUrn = UUID.randomUUID();
        log.info("Calling courtScheduleController.getCourtScheduleByCaseUrn with caseUrn: {}", caseUrn);
        final ResponseEntity<?> response = exampleController.getCourtScheduleByCaseUrn(caseUrn.toString());

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        final CourtScheduleResponse responseBody = (CourtScheduleResponse) response.getBody();
        assertNotNull(responseBody);
        assertNotNull(responseBody.getCourtSchedule());
        assertEquals(1, responseBody.getCourtSchedule().size());

        final CourtSchedule courtSchedule = responseBody.getCourtSchedule().getFirst();
        assertNotNull(courtSchedule.getHearings());
        assertEquals(1, courtSchedule.getHearings().size());

        final Hearing hearing = courtSchedule.getHearings().getFirst();
        assertNotNull(hearing.getHearingId());
        assertEquals("Requires interpreter", hearing.getListNote());
        assertEquals("Sentencing for theft case", hearing.getHearingDescription());
        assertEquals("Trial", hearing.getHearingType());
        assertNotNull(hearing.getCourtSittings());
        assertEquals(1, hearing.getCourtSittings().size());

        final CourtSitting courtSitting =
                hearing.getCourtSittings().getFirst();
        assertEquals("Central Criminal Court", courtSitting.getCourtHouse());
        assertNotNull(courtSitting.getSittingStart());
        assertTrue(courtSitting.getSittingEnd().isAfter(courtSitting.getSittingStart()));
        assertNotNull(courtSitting.getJudiciaryId());

    }

    @Test
    void get_court_schedule_by_case_urn_should_sanitize_case_urn() {
        final String unsanitizedCaseUrn = "<script>alert('xss')</script>";
        log.info("Calling courtScheduleController.getCourtScheduleByCaseUrn with unsanitized caseUrn: {}", unsanitizedCaseUrn);

        final ResponseEntity<?> response = exampleController.getCourtScheduleByCaseUrn(unsanitizedCaseUrn);
        assertNotNull(response);
        log.debug("Received response: {}", response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void get_judge_by_id_should_return_bad_request_status() {
        final ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> exampleController.getCourtScheduleByCaseUrn(null));
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).isEqualTo("caseUrn is required");
        assertThat(exception.getMessage()).isEqualTo("400 BAD_REQUEST \"caseUrn is required\"");
    }

} 