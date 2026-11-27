package uk.gov.hmcts.cp.models.crown;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.util.List;

/**
 * Crown court list from listing, Accept {@code application/vnd.listing.search.crown.list.payload+json}
 * ({@code GET /listing-service/query/api/rest/listing/dailylistpayload}). It is the daily list payload plus what
 * the CaTH PDDA lists need: linked cases, sitting notes, judge name parts and the full court address.
 * Only the fields the crown lists use are mapped; the rest are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CrownListPayload(
        Integer documentCounter,
        Boolean isWelsh,
        String courtCentreName,
        String welshCourtCentreName,
        List<CourtList> courtLists) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CourtList(CrestCourtSite crestCourtSite, List<Sitting> sittings) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CrestCourtSite(
            String crestCourtId,
            String crestCourtSiteId,
            String crestCourtSiteName,
            String welshCrestCourtSiteName,
            String crestCourtShortName,
            String welshCrestCourtShortName,
            String courtType,
            String courtCentreAddress1,
            String courtCentreAddress2,
            String courtCentreAddress3,
            String courtCentreAddress4,
            String courtCentreAddress5,
            String courtCentrePostcode,
            String welshCourtCentreAddress1,
            String welshCourtCentreAddress2,
            String welshCourtCentreAddress3,
            String welshCourtCentreAddress4,
            String welshCourtCentreAddress5) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sitting(
            LocalDate sittingDate,
            String courtRoomId,
            String courtRoomName,
            Boolean weekCommencing,
            String sittingNote,
            List<Judiciary> judiciary,
            List<Hearing> hearings) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Judiciary(
            Boolean isBenchChairman,
            Boolean isDeputy,
            String judiciaryName,
            String welshJudiciaryName,
            String judiciaryTitle,
            String welshJudiciaryTitle,
            String judiciaryForenames,
            String judiciarySurname,
            String judiciaryTitleSuffix,
            String welshJudiciaryTitleSuffix) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hearing(
            String startTime,
            HearingType hearingType,
            Boolean restrictFromCourtList,
            Boolean weekCommencing,
            String publicListNote,
            CaseIdentifier caseIdentifier,
            Prosecutor prosecutor,
            List<Defendant> defendants,
            List<LinkedCase> linkedCases,
            String applicationReference,
            Party applicant,
            List<Party> respondents,
            Party subject) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HearingType(String description, String welshDescription) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CaseIdentifier(String caseReference, String authorityCode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Prosecutor(String organisationName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Defendant(
            String firstName,
            String lastName,
            Boolean restrictFromCourtList,
            String defenceOrganisation,
            List<Offence> offences) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Offence(String offenceCode, StatementOfOffence statementOfOffence, Boolean restrictFromCourtList) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatementOfOffence(String title, String welshTitle) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LinkedCase(String caseUrn) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Party(String firstName, String lastName, Boolean restrictFromCourtList) {
    }
}
