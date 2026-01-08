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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourtListTransformationService {

    private static final DateTimeFormatter OUTPUT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DOB_FORMATTER = DateTimeFormatter.ofPattern("d MMM yyyy");

    public CourtListDocument transform(CourtListPayload payload) {
        log.info("Transforming court list payload to document format");

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
                        Session session = transformToSession(
                                payload,
                                hearingDate,
                                courtRoom
                        );
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

    private Session transformToSession(CourtListPayload payload, HearingDate hearingDate, CourtRoom courtRoom) {
        // Extract room number from courtRoomName (e.g., "Courtroom 01" -> 1)
        Integer roomNumber = extractRoomNumber(courtRoom.getCourtRoomName());

        // Get session start time from first hearing's startTime or use default
        String sessionStart = getSessionStartTime(courtRoom);

        // Get LJA (Local Justice Area) - using court centre name as placeholder
        // This might need to be adjusted based on actual data structure
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
        
        // Create a case for each defendant
        for (Defendant defendant : hearing.getDefendants()) {
            // Build defendant name
            String defName = buildDefendantName(defendant);

            // Convert date of birth format from "5 Jan 2006" to "05/01/2006"
            String defDob = convertDateOfBirth(defendant.getDateOfBirth());

            // Convert age from string to integer
            Integer defAge = convertAge(defendant.getAge());

            // Transform address
            uk.gov.hmcts.cp.models.transformed.Address defAddr = transformAddress(defendant.getAddress());

            // Transform offences
            Offences offences = transformOffences(defendant.getOffences());

            Case caseObj = Case.builder()
                    .caseno(hearing.getCaseNumber())
                    .defName(defName)
                    .defDob(defDob)
                    .defAge(defAge)
                    .defAddr(defAddr)
                    .inf(hearing.getProsecutorType())
                    .offences(offences)
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

    private String convertDateOfBirth(String dob) {
        if (dob == null || dob.trim().isEmpty()) {
            return null;
        }

        try {
            // Parse "5 Jan 2006" format
            LocalDate date = LocalDate.parse(dob.trim(), DOB_FORMATTER);
            return date.format(OUTPUT_DATE_FORMATTER);
        } catch (Exception e) {
            log.warn("Failed to parse date of birth: {}", dob, e);
            return null;
        }
    }

    private Integer convertAge(String age) {
        if (age == null || age.trim().isEmpty()) {
            return null;
        }

        try {
            return Integer.parseInt(age.trim());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse age: {}", age, e);
            return null;
        }
    }

    private uk.gov.hmcts.cp.models.transformed.Address transformAddress(uk.gov.hmcts.cp.models.Address address) {
        if (address == null) {
            // Return address with at least line1 (required field)
            return uk.gov.hmcts.cp.models.transformed.Address.builder()
                    .line1("")
                    .build();
        }

        String line1 = trimToMaxLength(address.getAddress1(), 35);
        // Ensure line1 is not null or empty (required field)
        if (line1 == null || line1.trim().isEmpty()) {
            line1 = "";
        }

        return uk.gov.hmcts.cp.models.transformed.Address.builder()
                .line1(line1)
                .line2(trimToMaxLength(address.getAddress2(), 35))
                .line3(trimToMaxLength(address.getAddress3(), 35))
                .line4(trimToMaxLength(address.getAddress4(), 35))
                .line5(trimToMaxLength(address.getAddress5(), 35))
                .pcode(trimToMaxLength(address.getPostcode(), 8))
                .build();
    }

    private Offences transformOffences(List<uk.gov.hmcts.cp.models.Offence> offences) {
        if (offences == null || offences.isEmpty()) {
            return null;
        }

        List<uk.gov.hmcts.cp.models.transformed.Offence> transformedOffences = offences.stream()
                .map(this::transformOffence)
                .collect(Collectors.toList());

        return Offences.builder()
                .offence(transformedOffences)
                .build();
    }

    private uk.gov.hmcts.cp.models.transformed.Offence transformOffence(uk.gov.hmcts.cp.models.Offence offence) {
        return uk.gov.hmcts.cp.models.transformed.Offence.builder()
                .code("") // CJS offence code - not available in source data
                .title(trimToMaxLength(offence.getOffenceTitle(), 120))
                .cyTitle(trimToMaxLength(offence.getWelshOffenceTitle(), 120))
                .sum(trimToMaxLength(offence.getOffenceWording(), 4000))
                .cySum("") // Welsh offence wording - not available in source data
                .build();
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

    private String trimToMaxLength(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }
}

