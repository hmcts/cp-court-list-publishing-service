package uk.gov.hmcts.cp.services;

import lombok.extern.slf4j.Slf4j;
import uk.gov.hmcts.cp.models.*;
import uk.gov.hmcts.cp.models.transformed.CourtListDocument;
import uk.gov.hmcts.cp.models.transformed.schema.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for court list transformation services.
 */
@Slf4j
public abstract class BaseCourtListTransformationService {

    protected static final DateTimeFormatter ISO_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    /**
     * Log message used at the start of transform (e.g. "Transforming progression court list...").
     */
    protected abstract String getTransformLogMessage();

    /**
     * Build venue address from payload. Subclasses define schema-specific address format.
     */
    protected abstract AddressSchema buildVenueAddressFromPayload(CourtListPayload payload);

    /**
     * Build court house from collected court rooms. Subclasses set courtHouseName/lja or leave them unset.
     */
    protected abstract CourtHouse buildCourtHouse(List<CourtRoomSchema> courtRooms, CourtListPayload payload);

    /**
     * Transform a hearing into schema format. Subclasses define case list and hearing schema fields.
     */
    protected abstract HearingSchema transformHearing(Hearing hearing);

    public final CourtListDocument transform(CourtListPayload payload) {
        log.info(getTransformLogMessage());

        String publicationDate = java.time.OffsetDateTime.now(ZoneOffset.UTC)
                .format(ISO_DATE_TIME_FORMATTER);

        DocumentSchema document = DocumentSchema.builder()
                .publicationDate(publicationDate)
                .build();

        Venue venue = transformVenue(payload);
        List<CourtList> courtLists = transformCourtLists(payload);

        return CourtListDocument.builder()
                .document(document)
                .venue(venue)
                .courtLists(courtLists)
                .build();
    }

    protected final Venue transformVenue(CourtListPayload payload) {
        return Venue.builder()
                .venueAddress(buildVenueAddressFromPayload(payload))
                .build();
    }

    protected static boolean isNonBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    protected final List<CourtList> transformCourtLists(CourtListPayload payload) {
        List<CourtList> courtLists = new ArrayList<>();
        List<CourtRoomSchema> courtRooms = collectCourtRooms(payload);

        if (!courtRooms.isEmpty()) {
            CourtHouse courtHouse = buildCourtHouse(courtRooms, payload);
            courtLists.add(CourtList.builder().courtHouse(courtHouse).build());
        }

        return courtLists;
    }

    protected final List<CourtRoomSchema> collectCourtRooms(CourtListPayload payload) {
        List<CourtRoomSchema> courtRooms = new ArrayList<>();

        if (payload.getHearingDates() != null && !payload.getHearingDates().isEmpty()) {
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
        }

        return courtRooms;
    }

    protected final CourtRoomSchema transformCourtRoom(CourtRoom courtRoom, HearingDate hearingDate) {
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

    protected final SessionSchema transformSession(CourtRoom courtRoom, HearingDate hearingDate) {
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

    protected final List<Judiciary> transformJudiciary(String judiciaryNames) {
        List<Judiciary> judiciary = new ArrayList<>();

        if (judiciaryNames != null && !judiciaryNames.trim().isEmpty()) {
            String[] names = judiciaryNames.split("[,;]");
            for (int i = 0; i < names.length; i++) {
                String name = names[i].trim();
                if (!name.isEmpty()) {
                    judiciary.add(Judiciary.builder()
                            .johKnownAs(name)
                            .isPresiding(i == 0)
                            .build());
                }
            }
        }

        return judiciary;
    }

    protected final Sitting transformSitting(Timeslot timeslot, HearingDate hearingDate) {
        if (timeslot.getHearings() == null || timeslot.getHearings().isEmpty()) {
            return null;
        }

        String sittingStart = convertToIsoDateTime(
                timeslot.getHearings().get(0).getStartTime(),
                hearingDate.getHearingDate());

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

    /**
     * Create a PROSECUTING_AUTHORITY party when prosecutor type is non-blank. Returns null otherwise.
     */
    protected final Party createProsecutorParty(String prosecutorType) {
        if (!isNonBlank(prosecutorType)) {
            return null;
        }
        return Party.builder()
                .partyRole("PROSECUTING_AUTHORITY")
                .individualDetails(null)
                .offence(null)
                .organisationDetails(OrganisationDetails.builder()
                        .organisationName(prosecutorType.trim())
                        .organisationAddress(null)
                        .build())
                .subject(null)
                .build();
    }

    protected final String convertToIsoDateTime(String time, String date) {
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
