package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.models.*;
import uk.gov.hmcts.cp.models.transformed.CourtListDocument;
import uk.gov.hmcts.cp.models.transformed.schema.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OnlinePublicCourtListTransformationService {

    private static final DateTimeFormatter ISO_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    public CourtListDocument transform(CourtListPayload payload) {
        log.info("Transforming progression public court list payload to document format");

        // Get current date/time for publicationDate (ISO 8601 format)
        String publicationDate = java.time.OffsetDateTime.now(ZoneOffset.UTC)
                .format(ISO_DATE_TIME_FORMATTER);

        DocumentSchema document = DocumentSchema.builder()
                .publicationDate(publicationDate)
                .build();

        // Transform venue address
        Venue venue = transformVenue(payload);

        // Transform court lists (simplified for public lists)
        List<CourtList> courtLists = transformCourtLists(payload);

        CourtListDocument result = CourtListDocument.builder()
                .document(document)
                .venue(venue)
                .courtLists(courtLists)
                .ouCode(payload.getOuCode())
                .courtId(payload.getCourtId())
                .courtIdNumeric(payload.getCourtIdNumeric())
                .build();

        return result;
    }

    private Venue transformVenue(CourtListPayload payload) {
        return Venue.builder()
                .venueAddress(buildVenueAddressFromPayload(payload))
                .build();
    }

    /**
     * Build venue address from payload address1, address2, address3, address4, address5 and postcode.
     * Schema requires venueAddress.line and venueAddress.postCode (never null).
     */
    private AddressSchema buildVenueAddressFromPayload(CourtListPayload payload) {
        List<String> lines = new ArrayList<>();
        if (isNonBlank(payload.getAddress1())) lines.add(payload.getAddress1().trim());
        if (isNonBlank(payload.getAddress2())) lines.add(payload.getAddress2().trim());
        if (isNonBlank(payload.getAddress3())) lines.add(payload.getAddress3().trim());
        if (isNonBlank(payload.getAddress4())) lines.add(payload.getAddress4().trim());
        if (isNonBlank(payload.getAddress5())) lines.add(payload.getAddress5().trim());

        String postcode = payload.getPostcode() != null ? payload.getPostcode().trim() : "";
        return AddressSchema.builder()
                .line(lines.isEmpty() ? new ArrayList<>() : lines)
                .postCode(postcode)
                .build();
    }

    private static boolean isNonBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private List<CourtList> transformCourtLists(CourtListPayload payload) {
        List<CourtList> courtLists = new ArrayList<>();

        if (payload.getHearingDates() != null && !payload.getHearingDates().isEmpty()) {
            List<CourtRoomSchema> courtRooms = new ArrayList<>();

            for (HearingDate hearingDate : payload.getHearingDates()) {
                if (hearingDate.getCourtRooms() != null) {
                    for (CourtRoom courtRoom : hearingDate.getCourtRooms()) {
                        CourtRoomSchema courtRoomSchema = transformCourtRoom(courtRoom, hearingDate);
                        if (courtRoomSchema != null) {
                            courtRooms.add(courtRoomSchema);
                        }
                    }
                }
            }

            if (!courtRooms.isEmpty()) {
                // According to public court list schema, courtHouse should not have courtHouseName or lja
                CourtHouse courtHouse = CourtHouse.builder()
                        .courtRoom(courtRooms)
                        .build();

                CourtList courtList = CourtList.builder()
                        .courtHouse(courtHouse)
                        .build();

                courtLists.add(courtList);
            }
        }

        return courtLists;
    }

    private CourtRoomSchema transformCourtRoom(CourtRoom courtRoom, HearingDate hearingDate) {
        List<SessionSchema> sessions = new ArrayList<>();

        if (courtRoom.getTimeslots() != null) {
            SessionSchema session = transformSession(courtRoom, hearingDate);
            if (session != null) {
                sessions.add(session);
            }
        }

        if (sessions.isEmpty()) {
            return null;
        }

        return CourtRoomSchema.builder()
                .courtRoomName(courtRoom.getCourtRoomName())
                .session(sessions)
                .build();
    }

    private SessionSchema transformSession(CourtRoom courtRoom, HearingDate hearingDate) {
        List<Judiciary> judiciary = transformJudiciary(courtRoom.getJudiciaryNames());
        List<Sitting> sittings = new ArrayList<>();

        if (courtRoom.getTimeslots() != null) {
            for (Timeslot timeslot : courtRoom.getTimeslots()) {
                if (timeslot.getHearings() != null && !timeslot.getHearings().isEmpty()) {
                    Sitting sitting = transformSitting(timeslot, hearingDate);
                    if (sitting != null) {
                        sittings.add(sitting);
                    }
                }
            }
        }

        if (sittings.isEmpty()) {
            return null;
        }

        return SessionSchema.builder()
                .judiciary(judiciary)
                .sittings(sittings)
                .build();
    }

    private List<Judiciary> transformJudiciary(String judiciaryNames) {
        List<Judiciary> judiciary = new ArrayList<>();
        
        if (judiciaryNames != null && !judiciaryNames.trim().isEmpty()) {
            String[] names = judiciaryNames.split("[,;]");
            for (int i = 0; i < names.length; i++) {
                String name = names[i].trim();
                if (!name.isEmpty()) {
                    Judiciary j = Judiciary.builder()
                            .johKnownAs(name)
                            .isPresiding(i == 0)
                            .build();
                    judiciary.add(j);
                }
            }
        }

        return judiciary;
    }

    private Sitting transformSitting(Timeslot timeslot, HearingDate hearingDate) {
        if (timeslot.getHearings() == null || timeslot.getHearings().isEmpty()) {
            return null;
        }

        String sittingStart = convertToIsoDateTime(timeslot.getHearings().get(0).getStartTime(), hearingDate.getHearingDate());

        List<HearingSchema> hearings = new ArrayList<>();
        for (Hearing hearing : timeslot.getHearings()) {
            HearingSchema hearingSchema = transformHearing(hearing);
            if (hearingSchema != null) {
                hearings.add(hearingSchema);
            }
        }

        if (hearings.isEmpty()) {
            return null;
        }

        return Sitting.builder()
                .sittingStart(sittingStart)
                .hearing(hearings)
                .build();
    }

    private HearingSchema transformHearing(Hearing hearing) {
        // For public lists, only include case number and defendant name
        List<CaseSchema> cases = transformCases(hearing);

        if (cases.isEmpty()) {
            return null;
        }

        // According to public court list schema, channel and application should be arrays (can be empty)
        List<String> channels = new ArrayList<>();
        List<Application> applications = new ArrayList<>();

        return HearingSchema.builder()
                .hearingType(hearing.getHearingType())
                .caseList(cases)
                .channel(channels)
                .application(applications)
                .build();
    }

    private List<CaseSchema> transformCases(Hearing hearing) {
        List<CaseSchema> cases = new ArrayList<>();

        if (hearing.getDefendants() == null || hearing.getDefendants().isEmpty()) {
            return cases;
        }

        // For public lists, create simplified cases with minimal party information
        for (Defendant defendant : hearing.getDefendants()) {
            List<Party> parties = new ArrayList<>();
            
            // Only include basic individual details (name only for public lists)
            if (defendant.getFirstName() != null || defendant.getSurname() != null) {
                IndividualDetails individualDetails = IndividualDetails.builder()
                        .individualForenames(defendant.getFirstName())
                        .individualSurname(defendant.getSurname())
                        .build();

                Party party = Party.builder()
                        .partyRole("DEFENDANT")
                        .individualDetails(individualDetails)
                        .build();

                parties.add(party);
            }

            CaseSchema caseSchema = CaseSchema.builder()
                    .caseUrn(hearing.getCaseNumber())
                    .reportingRestriction(hearing.getReportingRestrictionReason() != null && !hearing.getReportingRestrictionReason().trim().isEmpty())
                    .party(parties)
                    .build();

            cases.add(caseSchema);
        }

        return cases;
    }

    private String convertToIsoDateTime(String time, String date) {
        if (time == null || date == null) {
            return null;
        }

        try {
            LocalDate localDate = LocalDate.parse(date);
            String[] timeParts = time.split(":");
            int hour = timeParts.length > 0 ? Integer.parseInt(timeParts[0]) : 0;
            int minute = timeParts.length > 1 ? Integer.parseInt(timeParts[1]) : 0;
            int second = timeParts.length > 2 ? Integer.parseInt(timeParts[2]) : 0;

            java.time.LocalDateTime dateTime = localDate.atTime(hour, minute, second);
            return dateTime.atOffset(ZoneOffset.UTC).format(ISO_DATE_TIME_FORMATTER);
        } catch (Exception e) {
            log.warn("Failed to convert date/time to ISO format: date={}, time={}", date, time, e);
            return null;
        }
    }
}
