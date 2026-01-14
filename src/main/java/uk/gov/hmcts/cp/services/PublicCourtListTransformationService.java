package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.models.*;
import uk.gov.hmcts.cp.models.transformed.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PublicCourtListTransformationService {

    private static final DateTimeFormatter OUTPUT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public CourtListDocument transform(CourtListPayload payload) {
        log.info("Transforming public court list payload to document format");

        // Get current date for printdate
        String printDate = LocalDate.now().format(OUTPUT_DATE_FORMATTER);

        // Extract start time from courtCentreDefaultStartTime (format: HH:mm:ss)
        String startTime = payload.getCourtCentreDefaultStartTime() != null 
                ? payload.getCourtCentreDefaultStartTime() 
                : "00:00:00";

        DocumentInfo info = DocumentInfo.builder()
                .startTime(startTime)
                .build();

        List<Session> sessions = new ArrayList<>();

        if (payload.getHearingDates() != null) {
            for (HearingDate hearingDate : payload.getHearingDates()) {
                if (hearingDate.getCourtRooms() != null) {
                    for (CourtRoom courtRoom : hearingDate.getCourtRooms()) {
                        Session session = transformToSession(payload, courtRoom);
                        if (session != null) {
                            sessions.add(session);
                        }
                    }
                }
            }
        }

        Sessions sessionsWrapper = Sessions.builder()
                .session(sessions)
                .build();

        Job job = Job.builder()
                .printDate(printDate)
                .sessions(sessionsWrapper)
                .build();

        DocumentData data = DocumentData.builder()
                .job(job)
                .build();

        Document document = Document.builder()
                .info(info)
                .data(data)
                .build();

        return CourtListDocument.builder()
                .document(document)
                .build();
    }

    private Session transformToSession(CourtListPayload payload, CourtRoom courtRoom) {
        // Extract room number from courtRoomName (e.g., "Courtroom 01" -> 1)
        Integer roomNumber = extractRoomNumber(courtRoom.getCourtRoomName());

        // Get session start time from first hearing's startTime or use default
        String sessionStart = getSessionStartTime(courtRoom);

        // Get LJA (Local Justice Area) - using court centre name
        String lja = payload.getCourtCentreName();

        List<Block> blocks = new ArrayList<>();

        if (courtRoom.getTimeslots() != null) {
            for (Timeslot timeslot : courtRoom.getTimeslots()) {
                if (timeslot.getHearings() != null && !timeslot.getHearings().isEmpty()) {
                    // Get block start time from first hearing
                    String blockStart = timeslot.getHearings().get(0).getStartTime();

                    List<Case> cases = new ArrayList<>();
                    for (Hearing hearing : timeslot.getHearings()) {
                        List<Case> hearingCases = transformToCases(hearing);
                        if (hearingCases != null && !hearingCases.isEmpty()) {
                            cases.addAll(hearingCases);
                        }
                    }

                    if (!cases.isEmpty()) {
                        Cases casesWrapper = Cases.builder()
                                .caseList(cases)
                                .build();

                        Block block = Block.builder()
                                .bstart(blockStart)
                                .cases(casesWrapper)
                                .build();

                        blocks.add(block);
                    }
                }
            }
        }

        if (blocks.isEmpty()) {
            return null;
        }

        Blocks blocksWrapper = Blocks.builder()
                .block(blocks)
                .build();

        return Session.builder()
                .lja(lja)
                .court(payload.getCourtCentreName())
                .room(roomNumber)
                .sstart(sessionStart)
                .blocks(blocksWrapper)
                .build();
    }

    private List<Case> transformToCases(Hearing hearing) {
        if (hearing.getDefendants() == null || hearing.getDefendants().isEmpty()) {
            return new ArrayList<>();
        }

        List<Case> cases = new ArrayList<>();
        
        // Create a case for each defendant - simplified version with only caseno and def_name
        for (Defendant defendant : hearing.getDefendants()) {
            // Build defendant name
            String defName = buildDefendantName(defendant);

            // For public court list, only include caseno and def_name
            Case caseObj = Case.builder()
                    .caseno(hearing.getCaseNumber())
                    .defName(defName)
                    .build();
            
            cases.add(caseObj);
        }

        return cases;
    }

    private String buildDefendantName(Defendant defendant) {
        StringBuilder name = new StringBuilder();
        if (defendant.getFirstName() != null && !defendant.getFirstName().trim().isEmpty()) {
            name.append(defendant.getFirstName().trim());
        }
        if (defendant.getSurname() != null && !defendant.getSurname().trim().isEmpty()) {
            if (name.length() > 0) {
                name.append(" ");
            }
            name.append(defendant.getSurname().trim());
        }
        return name.toString();
    }

    private Integer extractRoomNumber(String courtRoomName) {
        if (courtRoomName == null || courtRoomName.trim().isEmpty()) {
            return 1; // Default room number
        }

        // Extract number from "Courtroom 01" or similar
        String cleaned = courtRoomName.replaceAll("[^0-9]", "");
        if (cleaned.isEmpty()) {
            return 1;
        }

        try {
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            log.warn("Failed to extract room number from: {}", courtRoomName, e);
            return 1;
        }
    }

    private String getSessionStartTime(CourtRoom courtRoom) {
        if (courtRoom.getTimeslots() != null && !courtRoom.getTimeslots().isEmpty()) {
            Timeslot firstTimeslot = courtRoom.getTimeslots().get(0);
            if (firstTimeslot.getHearings() != null && !firstTimeslot.getHearings().isEmpty()) {
                return firstTimeslot.getHearings().get(0).getStartTime();
            }
        }
        return "00:00";
    }
}


