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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StandardCourtListTransformationService {

    private static final DateTimeFormatter DOB_FORMATTER = DateTimeFormatter.ofPattern("d MMM yyyy");
    private static final DateTimeFormatter ISO_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    public CourtListDocument transform(CourtListPayload payload) {
        log.info("Transforming progression court list payload to document format");

        // Get current date/time for publicationDate (ISO 8601 format)
        String publicationDate = java.time.OffsetDateTime.now(ZoneOffset.UTC)
                .format(ISO_DATE_TIME_FORMATTER);

        DocumentSchema document = DocumentSchema.builder()
                .publicationDate(publicationDate)
                .build();

        // Transform venue address
        Venue venue = transformVenue(payload);

        // Transform court lists
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
        AddressSchema venueAddress = transformAddressSchemaFromStrings(
                payload.getCourtCentreAddress1(),
                payload.getCourtCentreAddress2()
        );

        return Venue.builder()
                .venueAddress(venueAddress)
                .build();
    }

    private List<CourtList> transformCourtLists(CourtListPayload payload) {
        List<CourtList> courtLists = new ArrayList<>();

        if (payload.getHearingDates() != null && !payload.getHearingDates().isEmpty()) {
            // Group by court house (using court centre name)
            String courtHouseName = payload.getCourtCentreName();
            String lja = payload.getCourtCentreName(); // Using court centre name as LJA placeholder

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
                CourtHouse courtHouse = CourtHouse.builder()
                        .courtHouseName(courtHouseName)
                        .lja(lja)
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
            // Group hearings by session (judiciary)
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
        // Transform judiciary
        List<Judiciary> judiciary = transformJudiciary(courtRoom.getJudiciaryNames());

        // Transform sittings
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
            // Split by comma or semicolon and create judiciary entries
            String[] names = judiciaryNames.split("[,;]");
            for (int i = 0; i < names.length; i++) {
                String name = names[i].trim();
                if (!name.isEmpty()) {
                    Judiciary j = Judiciary.builder()
                            .johKnownAs(name)
                            .isPresiding(i == 0) // First one is presiding
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

        // Get sitting start time from first hearing
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
        // Transform cases
        List<CaseSchema> cases = transformCases(hearing);

        if (cases.isEmpty()) {
            return null;
        }

        return HearingSchema.builder()
                .hearingType(hearing.getHearingType())
                .caseList(cases)
                .panel(hearing.getPanel())
                .channel(Collections.emptyList())
                .application(Collections.emptyList())
                .build();
    }

    private List<CaseSchema> transformCases(Hearing hearing) {
        List<CaseSchema> cases = new ArrayList<>();

        if (hearing.getDefendants() == null || hearing.getDefendants().isEmpty()) {
            return cases;
        }

        // Create a case for each defendant
        for (Defendant defendant : hearing.getDefendants()) {
            List<Party> parties = transformParties(defendant, hearing);

            CaseSchema caseSchema = CaseSchema.builder()
                    .caseUrn(hearing.getCaseNumber())
                    .reportingRestriction(hearing.getReportingRestrictionReason() != null && !hearing.getReportingRestrictionReason().trim().isEmpty())
                    .caseSequenceIndicator(null) // Not available in source data
                    .party(parties)
                    .build();

            cases.add(caseSchema);
        }

        return cases;
    }

    private List<Party> transformParties(Defendant defendant, Hearing hearing) {
        List<Party> parties = new ArrayList<>();

        // Determine party role - default to DEFENDANT if not specified
        String partyRole = "DEFENDANT"; // Default role

        // Transform individual details if defendant is an individual
        IndividualDetails individualDetails = null;
        if (defendant.getFirstName() != null || defendant.getSurname() != null) {
            individualDetails = IndividualDetails.builder()
                    .individualForenames(defendant.getFirstName())
                    .individualMiddleName(null) // Not available in source data
                    .individualSurname(defendant.getSurname())
                    .dateOfBirth(convertDateOfBirthToIso(defendant.getDateOfBirth()))
                    .age(convertAge(defendant.getAge()))
                    .address(transformAddressSchemaFromDefendant(defendant.getAddress()))
                    .inCustody(null) // Not available in source data
                    .gender(null) // Not available in source data
                    .asn(null) // Not available in source data
                    .build();
        }

        // Transform offences
        List<OffenceSchema> offences = transformOffenceSchemas(defendant.getOffences());

        // Transform organisation details if defendant is an organisation
        OrganisationDetails organisationDetails = null;
        if (defendant.getOrganisationName() != null && !defendant.getOrganisationName().trim().isEmpty()) {
            organisationDetails = OrganisationDetails.builder()
                    .organisationName(defendant.getOrganisationName())
                    .organisationAddress(transformAddressSchemaFromDefendant(defendant.getAddress()))
                    .build();
        }

        Party party = Party.builder()
                .partyRole(partyRole)
                .individualDetails(individualDetails)
                .offence(offences)
                .organisationDetails(organisationDetails)
                .subject(null) // Not available in source data
                .build();

        parties.add(party);

        // Add prosecutor as a party if available
        if (hearing.getProsecutorType() != null && !hearing.getProsecutorType().trim().isEmpty()) {
            Party prosecutorParty = Party.builder()
                    .partyRole("PROSECUTING_AUTHORITY")
                    .individualDetails(null)
                    .offence(null)
                    .organisationDetails(OrganisationDetails.builder()
                            .organisationName(hearing.getProsecutorType())
                            .organisationAddress(null)
                            .build())
                    .subject(null)
                    .build();
            parties.add(prosecutorParty);
        }

        return parties;
    }

    private List<OffenceSchema> transformOffenceSchemas(List<uk.gov.hmcts.cp.models.Offence> offences) {
        if (offences == null || offences.isEmpty()) {
            return null;
        }

        return offences.stream()
                .map(this::transformOffenceSchema)
                .collect(Collectors.toList());
    }

    private OffenceSchema transformOffenceSchema(uk.gov.hmcts.cp.models.Offence offence) {
        return OffenceSchema.builder()
                .offenceCode(offence.getId()) // Using ID as offence code
                .offenceTitle(offence.getOffenceTitle())
                .offenceWording(offence.getOffenceWording())
                .offenceMaxPen(null) // Not available in source data
                .reportingRestriction(null) // Not available in source data
                .convictionDate(null) // Not available in source data
                .adjournedDate(null) // Not available in source data
                .plea(null) // Not available in source data
                .pleaDate(null) // Not available in source data
                .offenceLegislation(null) // Not available in source data
                .build();
    }

    private AddressSchema transformAddressSchemaFromDefendant(uk.gov.hmcts.cp.models.Address address) {
        if (address == null) {
            return null;
        }

        return transformAddressSchemaFromStrings(
                address.getAddress1(),
                address.getAddress2(),
                address.getAddress3(),
                address.getAddress4(),
                address.getAddress5(),
                address.getPostcode()
        );
    }

    private AddressSchema transformAddressSchemaFromStrings(String address1, String address2) {
        return transformAddressSchemaFromStrings(address1, address2, null, null, null, null);
    }

    private AddressSchema transformAddressSchemaFromStrings(String address1, String address2, String address3, String address4, String address5, String postcode) {
        List<String> lines = new ArrayList<>();
        
        if (address1 != null && !address1.trim().isEmpty()) {
            lines.add(address1.trim());
        }
        if (address2 != null && !address2.trim().isEmpty()) {
            lines.add(address2.trim());
        }
        if (address3 != null && !address3.trim().isEmpty()) {
            lines.add(address3.trim());
        }
        if (address4 != null && !address4.trim().isEmpty()) {
            lines.add(address4.trim());
        }
        if (address5 != null && !address5.trim().isEmpty()) {
            lines.add(address5.trim());
        }

        // Extract town and county from address lines if possible
        // This is a simplified approach - may need adjustment based on actual data format
        String town = null;
        String county = null;
        if (lines.size() >= 2) {
            town = lines.get(lines.size() - 2);
            if (lines.size() >= 3) {
                county = lines.get(lines.size() - 3);
            }
        }

        return AddressSchema.builder()
                .line(lines)
                .town(town)
                .county(county)
                .postCode(postcode)
                .build();
    }

    private String convertDateOfBirthToIso(String dob) {
        if (dob == null || dob.trim().isEmpty()) {
            return null;
        }

        try {
            // Parse "5 Jan 2006" format and convert to ISO date format "yyyy-MM-dd" per schema
            LocalDate date = LocalDate.parse(dob.trim(), DOB_FORMATTER);
            return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
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

    private String convertToIsoDateTime(String time, String date) {
        if (time == null || date == null) {
            return null;
        }

        try {
            // Parse date (assuming format like "2026-01-05")
            LocalDate localDate = LocalDate.parse(date);
            
            // Parse time (assuming format like "10:00:00" or "10:00")
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
