package uk.gov.hmcts.cp.repositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.openapi.model.CourtSchedule;
import uk.gov.hmcts.cp.openapi.model.CourtScheduleResponse;
import uk.gov.hmcts.cp.openapi.model.CourtSitting;
import uk.gov.hmcts.cp.openapi.model.Hearing;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExampleRepositoryTest {

    private ExampleRepository exampleRepository;

    @BeforeEach
    void setUp() {
        exampleRepository = new ExampleInMemoryStubRepositoryImpl();
    }

    @Test
    void get_court_schedule_by_case_urn_should_return_court_schedule_response() {
        final UUID caseUrn = UUID.randomUUID();
        final CourtScheduleResponse response = exampleRepository.getCourtScheduleByCaseUrn(caseUrn.toString());

        assertNotNull(response.getCourtSchedule());
        assertEquals(1, response.getCourtSchedule().size());

        final CourtSchedule schedule = response.getCourtSchedule().getFirst();
        assertNotNull(schedule.getHearings());
        assertEquals(1, schedule.getHearings().size());

        final Hearing hearing = schedule.getHearings().getFirst();
        assertNotNull(hearing.getHearingId());
        assertEquals("Requires interpreter", hearing.getListNote());
        assertEquals("Sentencing for theft case", hearing.getHearingDescription());
        assertEquals("Trial", hearing.getHearingType());
        assertNotNull(hearing.getCourtSittings());
        assertEquals(1, hearing.getCourtSittings().size());

       final CourtSitting sitting =
                hearing.getCourtSittings().getFirst();
        assertEquals("Central Criminal Court", sitting.getCourtHouse());
        assertNotNull(sitting.getSittingStart());
        assertTrue(sitting.getSittingEnd().isAfter(sitting.getSittingStart()));
        assertNotNull(sitting.getJudiciaryId());
    }

}