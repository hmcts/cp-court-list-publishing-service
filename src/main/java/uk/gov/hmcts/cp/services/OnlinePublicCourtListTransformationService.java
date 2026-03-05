package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.models.*;
import uk.gov.hmcts.cp.models.transformed.schema.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OnlinePublicCourtListTransformationService extends BaseCourtListTransformationService {

    @Override
    protected String getTransformLogMessage() {
        return "Transforming progression public court list payload to document format";
    }

    @Override
    protected AddressSchema buildVenueAddressFromPayload(CourtListPayload payload) {
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

    @Override
    protected CourtHouse buildCourtHouse(List<CourtRoomSchema> courtRooms, CourtListPayload payload) {
        return CourtHouse.builder()
                .courtRoom(courtRooms)
                .build();
    }

    @Override
    protected HearingSchema transformHearing(Hearing hearing) {
        ApplicationTransformResult applicationTransformResult = transformApplications(hearing);
        List<CaseSchema> cases = transformCases(hearing, applicationTransformResult.subjectPartyId());

        if (cases.isEmpty()) {
            return null;
        }

        List<String> channels = new ArrayList<>();
        return HearingSchema.builder()
                .hearingType(hearing.getHearingType())
                .caseList(cases)
                .channel(channels)
                .application(applicationTransformResult.applications())
                .build();
    }

    /**
     * Transforms hearing court application data into schema Application list and subject party id.
     * Subject party id is derived here and used in buildApplicationParty (Party.subject) and in transformCases (defendant Party.subject).
     */
    @Override
    protected Party buildApplicationParty(CourtApplicationParty courtParty, String partyRole, String subjectPartyId) {
        if (courtParty == null) {
            return null;
        }
        IndividualDetails individualDetails = null;
        if (isNonBlank(courtParty.getName())) {
            individualDetails = IndividualDetails.builder()
                    .individualForenames(null)
                    .individualMiddleName(null)
                    .individualSurname(courtParty.getName())
                    .dateOfBirth(null)
                    .age(null)
                    .address(null)
                    .inCustody(null)
                    .gender(null)
                    .asn(null)
                    .build();
        }
        boolean isSubjectOfApplication = isNonBlank(courtParty.getId())
                && subjectPartyId != null
                && subjectPartyId.equals(courtParty.getId().trim());

        return Party.builder()
                .partyRole(partyRole)
                .individualDetails(individualDetails)
                .offence(null)
                .organisationDetails(null)
                .subject(isSubjectOfApplication)
                .build();
    }

    @Override
    protected List<Application> buildApplications(Hearing hearing, CourtApplication courtApplication, List<Party> parties) {
        Application application = Application.builder()
                .applicationReference(hearing.getCourtApplicationId().trim())
                .applicationType(null)
                .reportingRestriction(false)
                .party(parties.isEmpty() ? null : parties)
                .build();
        return Collections.singletonList(application);
    }

    private List<CaseSchema> transformCases(Hearing hearing, String subjectPartyId) {
        List<CaseSchema> cases = new ArrayList<>();

        if (hearing.getDefendants() == null || hearing.getDefendants().isEmpty()) {
            return cases;
        }

        for (Defendant defendant : hearing.getDefendants()) {
            List<Party> parties = new ArrayList<>();

            // Only include basic individual details (name only for public lists)
            IndividualDetails individualDetails = null;
            if (defendant.getFirstName() != null || defendant.getSurname() != null) {
                individualDetails = IndividualDetails.builder()
                        .individualForenames(defendant.getFirstName())
                        .individualSurname(defendant.getSurname())
                        .build();
            }

            // Offence list per schema (offenceTitle only for public lists)
            List<OffenceSchema> offences = transformOffencesForPublicList(defendant.getOffences(), defendant);

            boolean isSubjectOfApplication = isNonBlank(defendant.getId())
                    && subjectPartyId != null
                    && subjectPartyId.equals(defendant.getId().trim());

            parties.add(Party.builder()
                    .partyRole("DEFENDANT")
                    .individualDetails(individualDetails)
                    .offence(offences)
                    .subject(isSubjectOfApplication)
                    .build());

            Party prosecutorParty = createProsecutorParty(hearing.getProsecutorType());
            if (prosecutorParty != null) {
                parties.add(prosecutorParty);
            }

            List<String> reportingRestrictionDetails = getReportingRestrictionDetails(defendant);
            boolean hasReportingRestriction = hasReportingRestriction(defendant);
            cases.add(CaseSchema.builder()
                    .caseUrn(hearing.getCaseNumber())
                    .reportingRestriction(hasReportingRestriction)
                    .reportingRestrictionDetails(reportingRestrictionDetails)
                    .party(parties)
                    .build());
        }

        return cases;
    }

    /**
     * Resolves reporting restriction details from the defendant's reportingRestrictions array (labels).
     */
    private List<String> getReportingRestrictionDetails(Defendant defendant) {
        if (defendant.getReportingRestrictions() == null || defendant.getReportingRestrictions().isEmpty()) {
            return null;
        }
        List<String> labels = defendant.getReportingRestrictions().stream()
                .map(ReportingRestriction::getLabel)
                .filter(label -> label != null && !label.trim().isEmpty())
                .map(String::trim)
                .collect(Collectors.toList());
        return labels.isEmpty() ? null : labels;
    }

    private boolean hasReportingRestriction(Defendant defendant) {
        if (defendant.getReportingRestrictions() == null || defendant.getReportingRestrictions().isEmpty()) {
            return false;
        }
        return defendant.getReportingRestrictions().stream()
                .anyMatch(r -> r.getLabel() != null && !r.getLabel().trim().isEmpty());
    }

    /**
     * Transform offences for public list: schema only requires offenceTitle per offence item;
     * reporting restriction fields are populated from the defendant when present.
     */
    private List<OffenceSchema> transformOffencesForPublicList(List<uk.gov.hmcts.cp.models.Offence> offences, Defendant defendant) {
        if (offences == null || offences.isEmpty()) {
            return null;
        }
        List<String> offenceReportingDetails = null;
        Boolean offenceReportingRestriction = null;
        if (defendant != null && defendant.getReportingRestrictions() != null && !defendant.getReportingRestrictions().isEmpty()) {
            offenceReportingDetails = defendant.getReportingRestrictions().stream()
                    .map(ReportingRestriction::getLabel)
                    .filter(label -> label != null && !label.trim().isEmpty())
                    .map(String::trim)
                    .collect(Collectors.toList());
            offenceReportingRestriction = offenceReportingDetails != null && !offenceReportingDetails.isEmpty();
            if (offenceReportingDetails != null && offenceReportingDetails.isEmpty()) {
                offenceReportingDetails = null;
            }
        }
        final List<String> details = offenceReportingDetails;
        final Boolean restriction = offenceReportingRestriction;
        return offences.stream()
                .map(o -> OffenceSchema.builder()
                        .offenceTitle(o.getOffenceTitle())
                        .reportingRestriction(restriction)
                        .reportingRestrictionDetails(details)
                        .build())
                .collect(Collectors.toList());
    }
}
