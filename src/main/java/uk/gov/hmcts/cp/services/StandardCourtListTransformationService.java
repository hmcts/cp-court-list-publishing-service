package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.models.*;
import uk.gov.hmcts.cp.models.transformed.schema.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StandardCourtListTransformationService extends BaseCourtListTransformationService {

    /** Abbreviated month: "5 Jan 2006". September appears as "Sept" or "Sept." in data; Java expects "Sep". */
    private static final DateTimeFormatter DOB_FORMATTER = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK);
    /** Full month name fallback: "11 September 1972". */
    private static final DateTimeFormatter DOB_FORMATTER_FULL_MONTH = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK);
    private static final DateTimeFormatter ISO_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    /** Date-only pattern (YYYY-MM-DD) for normalising to ISO date-time for schema. */
    private static final Pattern DATE_ONLY_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    @Override
    protected String getTransformLogMessage() {
        return "Transforming progression court list payload to document format";
    }

    @Override
    protected AddressSchema buildVenueAddressFromPayload(CourtListPayload payload) {
        if (isNonBlank(payload.getAddress1()) || isNonBlank(payload.getAddress2()) || isNonBlank(payload.getAddress3())
                || isNonBlank(payload.getAddress4()) || isNonBlank(payload.getAddress5()) || isNonBlank(payload.getPostcode())) {
            return transformAddressSchemaFromStrings(
                    payload.getAddress1(), payload.getAddress2(), payload.getAddress3(),
                    payload.getAddress4(), payload.getAddress5(), payload.getPostcode());
        }
        return transformAddressSchemaFromStrings(payload.getCourtCentreAddress1(), payload.getCourtCentreAddress2());
    }

    @Override
    protected CourtHouse buildCourtHouse(List<CourtRoomSchema> courtRooms, CourtListPayload payload) {
        String courtHouseName = payload.getCourtCentreName();
        String lja = isNonBlank(payload.getLjaName()) ? payload.getLjaName() : payload.getCourtCentreName();
        return CourtHouse.builder()
                .courtHouseName(courtHouseName)
                .lja(lja)
                .courtRoom(courtRooms)
                .build();
    }

    @Override
    protected HearingSchema transformHearing(Hearing hearing) {
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

            List<String> reportingRestrictionDetails = null;
            if (isNonBlank(hearing.getReportingRestrictionReason())) {
                reportingRestrictionDetails = Collections.singletonList(hearing.getReportingRestrictionReason().trim());
            }
            CaseSchema caseSchema = CaseSchema.builder()
                    .caseUrn(hearing.getCaseNumber())
                    .reportingRestriction(hearing.getReportingRestrictionReason() != null && !hearing.getReportingRestrictionReason().trim().isEmpty())
                    .reportingRestrictionDetails(reportingRestrictionDetails)
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
                    .gender(defendant.getGender()) // Not available in source data
                    .asn(defendant.getAsn()) // Not available in source data
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

        boolean isSubjectOfApplication = hearing.getCourtApplication() != null
                && isDefendantSubjectOfApplication(defendant, hearing.getCourtApplication());

        parties.add(Party.builder()
                .partyRole(partyRole)
                .individualDetails(individualDetails)
                .offence(offences)
                .organisationDetails(organisationDetails)
                .subject(isSubjectOfApplication)
                .build());

        Party prosecutorParty = createProsecutorParty(hearing.getProsecutorType());
        if (prosecutorParty != null) {
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

    /**
     * Normalises a date string for schema fields that require ISO 8601 date-time (e.g. pleaDate, convictionDate, adjournedDate).
     * If the value is date-only (YYYY-MM-DD), appends T00:00:00.000Z; otherwise returns the value unchanged (or null).
     */
    private static String toIsoDateTimeOrNull(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        String trimmed = dateStr.trim();
        if (DATE_ONLY_PATTERN.matcher(trimmed).matches()) {
            return trimmed + "T00:00:00.000Z";
        }
        return trimmed;
    }

    private OffenceSchema transformOffenceSchema(uk.gov.hmcts.cp.models.Offence offence) {
        return OffenceSchema.builder()
                .offenceCode(offence.getOffenceCode())
                .offenceTitle(offence.getOffenceTitle())
                .offenceWording(offence.getOffenceWording())
                .offenceMaxPen(offence.getMaxPenalty())
                .reportingRestriction(null) // Not available in source data
                .reportingRestrictionDetails(null) // Not available in source data
                .convictionDate(toIsoDateTimeOrNull(offence.getConvictedOn())) // Not available in source data
                .adjournedDate(toIsoDateTimeOrNull(offence.getAdjournedDate())) // Not available in source data
                .plea(offence.getPlea()) // Not available in source data
                .pleaDate(toIsoDateTimeOrNull(offence.getPleaDate())) // Not available in source data
                .offenceLegislation(offence.getOffenceLegislation()) // Not available in source data
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

        // Normalise month variants: "Sept"/"Sept." → "Sep" (only September has alternate abbreviations in this data)
        String normalised = dob.trim()
                .replace("Sept. ", "Sep ")
                .replace("Sept ", "Sep ");

        try {
            // Try abbreviated month first: "5 Jan 2006", "11 Sep 1972"
            LocalDate date = LocalDate.parse(normalised, DOB_FORMATTER);
            return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e1) {
            try {
                // Fallback: full month name e.g. "11 September 1972"
                LocalDate date = LocalDate.parse(normalised, DOB_FORMATTER_FULL_MONTH);
                return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } catch (Exception e2) {
                log.warn("Failed to parse date of birth: {}", dob, e1);
                return null;
            }
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
}
