package uk.gov.hmcts.cp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import uk.gov.hmcts.cp.models.*;
import uk.gov.hmcts.cp.models.transformed.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CourtListTransformationServiceTest {

    @InjectMocks
    private CourtListTransformationService transformationService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CourtListPayload payload;

    @BeforeEach
    void setUp() throws Exception {
        payload = loadPayloadFromStubData("stubdata/court-list-payload-standard.json");
    }

    @Test
    void transform_shouldTransformAllFieldsCorrectly() throws Exception {
        // When
        CourtListDocument document = transformationService.transform(payload);

        // Then - Verify complete document structure
        assertThat(document).isNotNull();
        assertThat(document.getDocument()).isNotNull();
        
        // Verify DocumentInfo
        DocumentInfo info = document.getDocument().getInfo();
        assertThat(info).isNotNull();
        assertThat(info.getStartTime()).isEqualTo("10:00:00"); // From courtCentreDefaultStartTime
        
        // Verify DocumentData
        DocumentData data = document.getDocument().getData();
        assertThat(data).isNotNull();
        
        // Verify Job
        Job job = data.getJob();
        assertThat(job).isNotNull();
        
        // Verify printdate is current date in dd/MM/yyyy format
        String expectedPrintDate = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        assertThat(job.getPrintDate()).isEqualTo(expectedPrintDate);
        
        // Verify Sessions
        Sessions sessions = job.getSessions();
        assertThat(sessions).isNotNull();
        List<Session> sessionList = sessions.getSession();
        assertThat(sessionList).isNotEmpty();
        
        // Verify first Session
        Session session = sessionList.get(0);
        assertThat(session).isNotNull();
        assertThat(session.getLja()).isEqualTo("Test Court Centre"); // From courtCentreName
        assertThat(session.getCourt()).isEqualTo("Test Court Centre"); // From courtCentreName
        assertThat(session.getRoom()).isEqualTo(1); // Extracted from "Courtroom 01"
        assertThat(session.getSstart()).isEqualTo("10:30"); // From first hearing's startTime
        
        // Verify Blocks
        Blocks blocks = session.getBlocks();
        assertThat(blocks).isNotNull();
        List<Block> blockList = blocks.getBlock();
        assertThat(blockList).isNotEmpty();
        
        // Verify first Block
        Block block = blockList.get(0);
        assertThat(block).isNotNull();
        assertThat(block.getBstart()).isEqualTo("10:30"); // From first hearing's startTime in timeslot
        
        // Verify Cases
        Cases cases = block.getCases();
        assertThat(cases).isNotNull();
        List<Case> caseList = cases.getCaseList();
        assertThat(caseList).isNotEmpty();
        
        // Verify first Case
        Case caseObj = caseList.get(0);
        assertThat(caseObj).isNotNull();
        assertThat(caseObj.getCaseno()).isEqualTo("TEST123456"); // From hearing.caseNumber
        assertThat(caseObj.getDefName()).isEqualTo("John Doe"); // From firstName + surname
        assertThat(caseObj.getDefDob()).isEqualTo("05/01/2006"); // Converted from "5 Jan 2006"
        assertThat(caseObj.getDefAge()).isEqualTo(20); // Converted from "20"
        assertThat(caseObj.getInf()).isEqualTo("CITYPF"); // From hearing.prosecutorType
        
        // Verify Address transformation
        uk.gov.hmcts.cp.models.transformed.Address defAddr = caseObj.getDefAddr();
        assertThat(defAddr).isNotNull();
        assertThat(defAddr.getLine1()).isEqualTo("40 Market Place"); // From address.address1
        assertThat(defAddr.getLine2()).isEqualTo("Market Place"); // From address.address2
        assertThat(defAddr.getLine3()).isEqualTo("Bristol"); // From address.address3
        assertThat(defAddr.getPcode()).isEqualTo("NW1 5BR"); // From address.postcode
        
        // Verify Offences transformation
        Offences offences = caseObj.getOffences();
        assertThat(offences).isNotNull();
        List<uk.gov.hmcts.cp.models.transformed.Offence> offenceList = offences.getOffence();
        assertThat(offenceList).isNotEmpty();
        
        // Verify first Offence
        uk.gov.hmcts.cp.models.transformed.Offence offence = offenceList.get(0);
        assertThat(offence).isNotNull();
        assertThat(offence.getCode()).isEqualTo(""); // Not available in source data
        assertThat(offence.getTitle()).isEqualTo("Attempt theft of motor vehicle"); // From offenceTitle
        assertThat(offence.getCyTitle()).isEqualTo("Ymgais i ddwyn cerbyd modur"); // From welshOffenceTitle
        assertThat(offence.getSum()).isEqualTo("Attempt theft to vehicle"); // From offenceWording
        assertThat(offence.getCySum()).isEqualTo(""); // Not available in source data
    }

    @Test
    void transform_shouldHandleMultipleHearingDates() throws Exception {
        // Given
        payload = loadPayloadFromStubData("stubdata/court-list-payload-multiple-dates.json");

        // When
        CourtListDocument document = transformationService.transform(payload);

        // Then
        assertThat(document).isNotNull();
        List<Session> sessions = document.getDocument().getData().getJob().getSessions().getSession();
        assertThat(sessions.size()).isEqualTo(2); // One session per hearing date/court room
        
        // Verify first session
        Session session1 = sessions.get(0);
        assertThat(session1.getRoom()).isEqualTo(1); // From "Courtroom 01"
        assertThat(session1.getSstart()).isEqualTo("10:00"); // From first hearing's startTime
        
        // Verify second session
        Session session2 = sessions.get(1);
        assertThat(session2.getRoom()).isEqualTo(2); // From "Courtroom 02"
        assertThat(session2.getSstart()).isEqualTo("11:00"); // From first hearing's startTime
    }

    @Test
    void transform_shouldHandleEmptyHearingDates() throws Exception {
        // Given
        payload = loadPayloadFromStubData("stubdata/court-list-payload-empty-hearings.json");

        // When
        CourtListDocument document = transformationService.transform(payload);

        // Then
        assertThat(document).isNotNull();
        assertThat(document.getDocument()).isNotNull();
        assertThat(document.getDocument().getData()).isNotNull();
        assertThat(document.getDocument().getData().getJob()).isNotNull();
        
        // Sessions should exist but be empty
        Sessions sessions = document.getDocument().getData().getJob().getSessions();
        assertThat(sessions).isNotNull();
        assertThat(sessions.getSession()).isEmpty();
    }

    /**
     * Loads CourtListPayload from a stub data JSON file
     */
    private CourtListPayload loadPayloadFromStubData(String resourcePath) throws Exception {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        String json = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        return objectMapper.readValue(json, CourtListPayload.class);
    }
}
