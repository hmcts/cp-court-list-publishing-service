package uk.gov.hmcts.cp.services.crown;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.models.crown.CrownListPayload;
import uk.gov.hmcts.cp.models.crown.CrownListPayload.CourtList;
import uk.gov.hmcts.cp.models.crown.CrownListPayload.CrestCourtSite;
import uk.gov.hmcts.cp.models.crown.CrownListPayload.Defendant;
import uk.gov.hmcts.cp.models.crown.CrownListPayload.Hearing;
import uk.gov.hmcts.cp.models.crown.CrownListPayload.Judiciary;
import uk.gov.hmcts.cp.models.crown.CrownListPayload.LinkedCase;
import uk.gov.hmcts.cp.models.crown.CrownListPayload.Offence;
import uk.gov.hmcts.cp.models.crown.CrownListPayload.Party;
import uk.gov.hmcts.cp.models.crown.CrownListPayload.Sitting;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Builds the CaTH PDDA crown list documents (Daily, Firm and Warned/Advance lists, schemas
 * {@code src/main/resources/schema/crown_*_pdda_list.json}) from listing's crown list payload.
 *
 * <ul>
 *   <li>The list covers the requested dates: the header uses them, and sittings listing returns for other days
 *   are left out. A sitting without a date is left out of the daily and firm lists; the warned list shows it
 *   without a fixed date.</li>
 *   <li>Restricted data is never published: hearings, defendants, offences and parties with
 *   {@code restrictFromCourtList=true}, hearings whose parties are all restricted, and parties without a name
 *   are left out.</li>
 *   <li>Text is used as given; it is cleaned before the payload is parsed.</li>
 * </ul>
 */
@Component
@Slf4j
public class CrownListTransformer {

    /** Judge surname when a sitting has no judiciary allocated; the CaTH schemas require one. */
    static final String JUDGE_NOT_ALLOCATED = "To be allocated";

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final ZoneId UK = ZoneId.of("Europe/London");
    private static final DateTimeFormatter SITTING_AT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter TIME_MARKING = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final int MAX_NAME_LENGTH = 35;
    private static final int MAX_ADDRESS_LINES = 5;
    private static final int MAX_JUSTICES = 4;
    private static final String CROWN_COURT = "Crown Court";
    private static final String DEFAULT_HEARING_DESCRIPTION = "Hearing";
    private static final String OTHER_PROSECUTOR = "Other Prosecutor";
    private static final List<String> PROSECUTING_AUTHORITIES = List.of(
            "Crown Prosecution Service", "Customs and Excise", "Department of Trade and Industry", "Inland Revenue");

    private final Clock clock;

    public CrownListTransformer() {
        this(Clock.systemUTC());
    }

    CrownListTransformer(Clock clock) {
        this.clock = clock;
    }

    /**
     * The CaTH document, and how many of the payload's hearings it lists.
     *
     * @param hearingsInPayload hearings listing returned for the court centre (any day, any restriction)
     * @param hearingsListed    hearings in the document
     */
    public record Result(ObjectNode document, int hearingsInPayload, int hearingsListed) {
    }

    public Result transform(CrownPublishRequest request, CrownListPayload payload) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(payload, "payload");
        CrownListType type = request.listType();
        boolean welsh = Boolean.TRUE.equals(payload.isWelsh());
        Build build = new Build(type, welsh);

        ObjectNode list = NODES.objectNode();
        list.set("DocumentID", documentId(request));
        list.set("ListHeader", listHeader(request, payload));
        list.set("CrownCourt", crownCourt(payload, request.courtTelephone(), welsh));
        List<CourtList> courtLists = inRequestedPeriod(request, payload);
        list.set("CourtLists", type == CrownListType.WARNED ? build.warnedCourtLists(courtLists) : build.sittingCourtLists(courtLists));

        ObjectNode document = NODES.objectNode();
        document.set(type.rootElement(), list);
        return new Result(document, countHearings(payload), build.hearingsListed);
    }

    // ---- header ----------------------------------------------------------------------------------------------

    private static ObjectNode documentId(CrownPublishRequest request) {
        ObjectNode node = NODES.objectNode();
        node.put("UniqueID", request.courtListId().toString());
        node.put("DocumentType", request.listType().documentType());
        return node;
    }

    private ObjectNode listHeader(CrownPublishRequest request, CrownListPayload payload) {
        ObjectNode node = NODES.objectNode();
        node.put("StartDate", request.startDate().toString());
        node.put("EndDate", request.endDate().toString());
        node.put("Version", payload.documentCounter() == null ? "1" : String.valueOf(payload.documentCounter()));
        // Instant.toString always writes the seconds, as an RFC 3339 date-time needs
        node.put("PublishedTime", Instant.now(clock).truncatedTo(ChronoUnit.SECONDS).toString());
        return node;
    }

    private static ObjectNode crownCourt(CrownListPayload payload, String telephone, boolean welsh) {
        CrestCourtSite site = nullSafe(payload.courtLists()).stream()
                .map(CourtList::crestCourtSite).filter(Objects::nonNull).findFirst().orElse(null);
        String name = localised(welsh, payload.courtCentreName(), payload.welshCourtCentreName());
        if (isBlank(name) && site != null) {
            name = localised(welsh, site.crestCourtSiteName(), site.welshCrestCourtSiteName());
        }
        ObjectNode node = NODES.objectNode();
        node.put("CourtHouseType", CROWN_COURT);
        node.put("CourtHouseCode", courtHouseCode(site));
        node.put("CourtHouseName", nullToEmpty(name));
        putIfPresent(node, "CourtHouseAddress", address(site, welsh));
        putIfNotBlank(node, "CourtHouseTelephone", telephone);
        return node;
    }

    private static ObjectNode courtHouse(CrestCourtSite site, boolean welsh) {
        ObjectNode node = NODES.objectNode();
        node.put("CourtHouseType", courtHouseType(site == null ? null : site.courtType()));
        node.put("CourtHouseCode", courtHouseCode(site));
        if (site != null) {
            putIfNotBlank(node, "CourtHouseShortName", localised(welsh, site.crestCourtShortName(), site.welshCrestCourtShortName()));
        }
        node.put("CourtHouseName", site == null ? "" : nullToEmpty(localised(welsh, site.crestCourtSiteName(), site.welshCrestCourtSiteName())));
        putIfPresent(node, "CourtHouseAddress", address(site, welsh));
        return node;
    }

    private static ObjectNode address(CrestCourtSite site, boolean welsh) {
        if (site == null) {
            return null;
        }
        List<String> lines = nonBlank(site.courtCentreAddress1(), site.courtCentreAddress2(), site.courtCentreAddress3(),
                site.courtCentreAddress4(), site.courtCentreAddress5());
        if (welsh) {
            List<String> welshLines = nonBlank(site.welshCourtCentreAddress1(), site.welshCourtCentreAddress2(),
                    site.welshCourtCentreAddress3(), site.welshCourtCentreAddress4(), site.welshCourtCentreAddress5());
            if (!welshLines.isEmpty()) {
                lines = welshLines;
            }
        }
        if (lines.isEmpty() && isBlank(site.courtCentrePostcode())) {
            return null;
        }
        ObjectNode node = NODES.objectNode();
        if (!lines.isEmpty()) {
            ArrayNode lineNodes = node.putArray("Line");
            lines.stream().limit(MAX_ADDRESS_LINES).forEach(lineNodes::add);
        }
        putIfNotBlank(node, "PostCode", site.courtCentrePostcode());
        return node;
    }

    // ---- period filter ---------------------------------------------------------------------------------------

    private static List<CourtList> inRequestedPeriod(CrownPublishRequest request, CrownListPayload payload) {
        return nullSafe(payload.courtLists()).stream()
                .map(courtList -> new CourtList(courtList.crestCourtSite(), nullSafe(courtList.sittings()).stream()
                        .filter(sitting -> inPeriod(request, sitting))
                        .toList()))
                .toList();
    }

    private static boolean inPeriod(CrownPublishRequest request, Sitting sitting) {
        LocalDate date = sitting.sittingDate();
        if (date == null) {
            if (request.listType() == CrownListType.WARNED) {
                return true;
            }
            log.warn("Crown list sitting without a sitting date left out of the {}, courtRoomId={}",
                    request.describe(), sitting.courtRoomId());
            return false;
        }
        if (date.isBefore(request.startDate()) || date.isAfter(request.endDate())) {
            log.warn("Crown list sitting on {} left out: outside the {}, courtRoomId={}",
                    date, request.describe(), sitting.courtRoomId());
            return false;
        }
        return true;
    }

    private static int countHearings(CrownListPayload payload) {
        return nullSafe(payload.courtLists()).stream()
                .flatMap(courtList -> nullSafe(courtList.sittings()).stream())
                .mapToInt(sitting -> nullSafe(sitting.hearings()).size())
                .sum();
    }

    /** Builds the court lists of one document and counts the hearings it lists. */
    private static final class Build {

        private final CrownListType type;
        private final boolean welsh;
        private int hearingsListed;

        Build(CrownListType type, boolean welsh) {
            this.type = type;
            this.welsh = welsh;
        }

        // ---- daily and firm lists ----------------------------------------------------------------------------

        ArrayNode sittingCourtLists(List<CourtList> courtLists) {
            ArrayNode nodes = NODES.arrayNode();
            for (CourtList courtList : courtLists) {
                if (type == CrownListType.FIRM) {
                    Map<LocalDate, List<Sitting>> byDate = new TreeMap<>();
                    courtList.sittings().forEach(sitting ->
                            byDate.computeIfAbsent(sitting.sittingDate(), d -> new ArrayList<>()).add(sitting));
                    byDate.forEach((date, sittings) -> {
                        ArrayNode sittingNodes = sittings(sittings, date);
                        if (!sittingNodes.isEmpty()) {
                            ObjectNode node = nodes.addObject();
                            node.put("SittingDate", date.toString());
                            node.set("CourtHouse", courtHouse(courtList.crestCourtSite(), welsh));
                            node.set("Sittings", sittingNodes);
                        }
                    });
                } else {
                    ArrayNode sittingNodes = sittings(courtList.sittings(), null);
                    if (!sittingNodes.isEmpty()) {
                        ObjectNode node = nodes.addObject();
                        node.set("CourtHouse", courtHouse(courtList.crestCourtSite(), welsh));
                        node.set("Sittings", sittingNodes);
                    }
                }
            }
            return nodes;
        }

        private ArrayNode sittings(List<Sitting> sittings, LocalDate hearingDate) {
            ArrayNode nodes = NODES.arrayNode();
            for (Sitting sitting : sittings) {
                List<TimedHearing> hearings = publishableHearings(sitting);
                if (hearings.isEmpty()) {
                    continue;
                }
                ObjectNode node = nodes.addObject();
                node.put("CourtRoomNumber", courtRoomNumber(sitting.courtRoomName()));
                hearings.stream()
                        .filter(hearing -> hasFixedTime(sitting, hearing.hearing()))
                        .map(TimedHearing::time)
                        .flatMap(Optional::stream)
                        .min(Comparator.naturalOrder())
                        .ifPresent(time -> node.put("SittingAt", time.format(SITTING_AT)));
                putIfNotBlank(node, "SittingNote", sitting.sittingNote());
                node.set("Judiciary", judiciary(sitting.judiciary()));

                ArrayNode hearingNodes = node.putArray("Hearings");
                int sequence = 1;
                for (TimedHearing hearing : hearings) {
                    hearingNodes.add(sittingHearing(sitting, hearing, sequence++, hearingDate));
                }
                hearingsListed += hearings.size();
            }
            return nodes;
        }

        /** On the firm list a week commencing sitting or hearing has no fixed time; on the daily list all do. */
        private boolean hasFixedTime(Sitting sitting, Hearing hearing) {
            return type != CrownListType.FIRM
                    || !(Boolean.TRUE.equals(sitting.weekCommencing()) || Boolean.TRUE.equals(hearing.weekCommencing()));
        }

        private ObjectNode sittingHearing(Sitting sitting, TimedHearing timed, int sequence, LocalDate hearingDate) {
            Hearing hearing = timed.hearing();
            ObjectNode node = NODES.objectNode();
            node.put("HearingSequenceNumber", sequence);
            ObjectNode details = node.putObject("HearingDetails");
            details.put("HearingDescription", hearingDescription(hearing, welsh));
            if (hearingDate != null) {
                details.put("HearingDate", hearingDate.toString());
            }
            if (hasFixedTime(sitting, hearing)) {
                timed.time().ifPresent(time -> node.put("TimeMarkingNote", time.format(TIME_MARKING)));
            }
            String caseNumber = caseNumber(hearing);
            node.put("CaseNumber", caseNumber);
            node.put("CaseNumberCaTH", caseNumber);
            putIfPresent(node, "Prosecution", prosecution(hearing, true));
            putIfNotBlank(node, "ListNote", hearing.publicListNote());
            putIfNotEmpty(node, "Defendants", defendants(hearing, type == CrownListType.FIRM, welsh));
            ArrayNode respondents = NODES.arrayNode();
            nullSafe(hearing.respondents()).stream()
                    .filter(CrownListTransformer::isPublishable)
                    .map(party -> (nullToEmpty(party.firstName()) + " " + nullToEmpty(party.lastName())).trim())
                    .filter(name -> !name.isEmpty())
                    .forEach(respondents::add);
            putIfNotEmpty(node, "Respondent", respondents);
            return node;
        }

        // ---- warned (advance) list ---------------------------------------------------------------------------

        ArrayNode warnedCourtLists(List<CourtList> courtLists) {
            ArrayNode nodes = NODES.arrayNode();
            for (CourtList courtList : courtLists) {
                Map<LocalDate, ArrayNode> fixed = new TreeMap<>();
                ArrayNode unfixed = NODES.arrayNode();
                for (Sitting sitting : courtList.sittings()) {
                    for (TimedHearing timed : publishableHearings(sitting)) {
                        Hearing hearing = timed.hearing();
                        boolean hasFixedDate = sitting.sittingDate() != null
                                && !Boolean.TRUE.equals(sitting.weekCommencing())
                                && !Boolean.TRUE.equals(hearing.weekCommencing());
                        if (hasFixedDate) {
                            fixed.computeIfAbsent(sitting.sittingDate(), d -> NODES.arrayNode())
                                    .add(warnedCase(hearing, sitting.sittingDate()));
                        } else {
                            unfixed.add(warnedCase(hearing, null));
                        }
                        hearingsListed++;
                    }
                }
                if (fixed.isEmpty() && unfixed.isEmpty()) {
                    continue;
                }
                ObjectNode node = nodes.addObject();
                node.set("CourtHouse", courtHouse(courtList.crestCourtSite(), welsh));
                if (!fixed.isEmpty()) {
                    ArrayNode fixtures = node.putArray("WithFixedDate").addObject().putArray("Fixture");
                    fixed.forEach((date, cases) -> {
                        ObjectNode fixture = fixtures.addObject();
                        fixture.put("FixedDate", date.toString());
                        fixture.set("Cases", cases);
                    });
                }
                if (!unfixed.isEmpty()) {
                    node.putArray("WithoutFixedDate").addObject().putArray("Fixture").addObject().set("Cases", unfixed);
                }
            }
            return nodes;
        }

        private ObjectNode warnedCase(Hearing hearing, LocalDate fixedDate) {
            ObjectNode node = NODES.objectNode();
            String caseNumber = caseNumber(hearing);
            node.put("CaseNumber", caseNumber);
            node.put("CaseNumberCaTH", caseNumber);
            ObjectNode hearingNode = node.putArray("Hearing").addObject();
            hearingNode.put("HearingDescription", hearingDescription(hearing, welsh));
            if (fixedDate != null) {
                hearingNode.put("HearingDate", fixedDate.toString());
            }
            putIfNotBlank(hearingNode, "ListNote", hearing.publicListNote());
            node.set("Defendants", defendants(hearing, true, welsh));
            putIfPresent(node, "Prosecution", prosecution(hearing, false));
            ArrayNode linkedCases = NODES.arrayNode();
            nullSafe(hearing.linkedCases()).stream()
                    .map(LinkedCase::caseUrn)
                    .filter(urn -> !isBlank(urn))
                    .forEach(urn -> linkedCases.addObject().put("CaseNumber", urn));
            putIfNotEmpty(node, "LinkedCases", linkedCases);
            return node;
        }

        private ObjectNode judiciary(List<Judiciary> judiciary) {
            List<Judiciary> members = nullSafe(judiciary);
            ObjectNode node = NODES.objectNode();
            if (members.isEmpty()) {
                node.putObject("Judge").put("CitizenNameSurname", JUDGE_NOT_ALLOCATED);
                return node;
            }
            Judiciary presiding = presidingJudge(members);
            node.set("Judge", judgeName(presiding, welsh));
            if (members.size() > 1) {
                ArrayNode justices = node.putArray("Justice");
                members.stream().filter(member -> member != presiding).limit(MAX_JUSTICES)
                        .map(member -> judgeName(member, welsh)).forEach(justices::add);
            }
            return node;
        }
    }

    // ---- hearings --------------------------------------------------------------------------------------------

    /** A hearing with its start time, parsed once. */
    private record TimedHearing(Hearing hearing, Optional<LocalTime> time) {
    }

    private static List<TimedHearing> publishableHearings(Sitting sitting) {
        return nullSafe(sitting.hearings()).stream()
                .filter(hearing -> !Boolean.TRUE.equals(hearing.restrictFromCourtList()))
                .filter(hearing -> !allPartiesRestricted(hearing))
                .filter(CrownListTransformer::hasCaseNumber)
                .map(hearing -> new TimedHearing(hearing, startTime(hearing.startTime())))
                .sorted(Comparator.comparing((TimedHearing timed) -> timed.time().orElse(null),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /**
     * A hearing whose parties are all restricted is left out: listing it without them would still reveal the
     * case number, prosecutor and list note of a restricted case. A hearing without parties is kept.
     */
    private static boolean allPartiesRestricted(Hearing hearing) {
        List<Defendant> defendants = nullSafe(hearing.defendants());
        if (!defendants.isEmpty()) {
            return defendants.stream().allMatch(defendant -> Boolean.TRUE.equals(defendant.restrictFromCourtList()));
        }
        return Stream.of(hearing.subject(), hearing.applicant()).filter(Objects::nonNull).findFirst()
                .map(party -> !isPublishable(party))
                .orElse(false);
    }

    private static boolean hasCaseNumber(Hearing hearing) {
        if (isBlank(caseNumber(hearing))) {
            log.warn("Crown list hearing left out: no case or application reference (start {})", hearing.startTime());
            return false;
        }
        return true;
    }

    private static String caseNumber(Hearing hearing) {
        if (hearing.caseIdentifier() != null && !isBlank(hearing.caseIdentifier().caseReference())) {
            return hearing.caseIdentifier().caseReference().trim();
        }
        return isBlank(hearing.applicationReference()) ? "" : hearing.applicationReference().trim();
    }

    private static String hearingDescription(Hearing hearing, boolean welsh) {
        String description = hearing.hearingType() == null ? null
                : localised(welsh, hearing.hearingType().description(), hearing.hearingType().welshDescription());
        return isBlank(description) ? DEFAULT_HEARING_DESCRIPTION : description;
    }

    private static ObjectNode prosecution(Hearing hearing, boolean withReference) {
        ObjectNode node = NODES.objectNode();
        if (withReference && hearing.caseIdentifier() != null) {
            putIfNotBlank(node, "ProsecutingReference", hearing.caseIdentifier().authorityCode());
        }
        String organisation = hearing.prosecutor() == null ? null : hearing.prosecutor().organisationName();
        if (!isBlank(organisation)) {
            node.putObject("ProsecutingOrganisation").put("OrganisationName", organisation);
            node.put("ProsecutingAuthority", prosecutingAuthority(organisation.trim()));
        }
        return node.isEmpty() ? null : node;
    }

    private static String prosecutingAuthority(String organisation) {
        if ("CPS".equalsIgnoreCase(organisation)) {
            return PROSECUTING_AUTHORITIES.get(0);
        }
        return PROSECUTING_AUTHORITIES.stream().filter(organisation::equalsIgnoreCase).findFirst().orElse(OTHER_PROSECUTOR);
    }

    /** The hearing's defendants; for an application hearing without defendants, its subject or else its applicant. */
    private static ArrayNode defendants(Hearing hearing, boolean withCounsel, boolean welsh) {
        ArrayNode nodes = NODES.arrayNode();
        List<Defendant> defendants = nullSafe(hearing.defendants());
        if (defendants.isEmpty()) {
            Stream.of(hearing.subject(), hearing.applicant()).filter(Objects::nonNull).findFirst()
                    .filter(CrownListTransformer::isPublishable)
                    .filter(party -> hasName(party.firstName(), party.lastName()))
                    .ifPresent(party -> nodes.addObject().set("PersonalDetails", personalDetails(party.firstName(), party.lastName())));
            return nodes;
        }
        String urn = hearing.caseIdentifier() == null ? null : hearing.caseIdentifier().caseReference();
        for (Defendant defendant : defendants) {
            if (Boolean.TRUE.equals(defendant.restrictFromCourtList()) || !hasName(defendant.firstName(), defendant.lastName())) {
                continue;
            }
            ObjectNode node = nodes.addObject();
            node.set("PersonalDetails", personalDetails(defendant.firstName(), defendant.lastName()));
            putIfNotBlank(node, "URN", urn);
            if (withCounsel && !isBlank(defendant.defenceOrganisation())) {
                node.putArray("Counsel").addObject().putArray("Solicitor").addObject().putObject("Party")
                        .putObject("Organisation").put("OrganisationName", defendant.defenceOrganisation());
            }
            putIfNotEmpty(node, "Charges", charges(defendant.offences(), welsh));
        }
        return nodes;
    }

    private static boolean hasName(String firstName, String lastName) {
        if (isBlank(firstName) && isBlank(lastName)) {
            log.warn("Crown list party without a name left out");
            return false;
        }
        return true;
    }

    private static ArrayNode charges(List<Offence> offences, boolean welsh) {
        ArrayNode nodes = NODES.arrayNode();
        for (Offence offence : nullSafe(offences)) {
            if (Boolean.TRUE.equals(offence.restrictFromCourtList())) {
                continue;
            }
            String statement = offence.statementOfOffence() == null ? null
                    : localised(welsh, offence.statementOfOffence().title(), offence.statementOfOffence().welshTitle());
            if (isBlank(statement)) {
                statement = offence.offenceCode();
            }
            if (!isBlank(statement)) {
                ObjectNode node = nodes.addObject();
                node.put("OffenceStatement", statement);
                putIfNotBlank(node, "CJSoffenceCode", offence.offenceCode());
            }
        }
        return nodes;
    }

    private static ObjectNode personalDetails(String firstName, String lastName) {
        ObjectNode name = NODES.objectNode();
        if (isBlank(lastName)) {
            name.put("CitizenNameSurname", truncate(firstName));
        } else {
            if (!isBlank(firstName)) {
                name.putArray("CitizenNameForename").add(firstName.trim());
            }
            name.put("CitizenNameSurname", truncate(lastName));
        }
        ObjectNode node = NODES.objectNode();
        node.set("Name", name);
        node.put("IsMasked", "no");
        return node;
    }

    /** The judge the sitting is listed under: the bench chairman, else the first who is not a deputy, else the first. */
    private static Judiciary presidingJudge(List<Judiciary> members) {
        return members.stream().filter(member -> Boolean.TRUE.equals(member.isBenchChairman())).findFirst()
                .or(() -> members.stream().filter(member -> !Boolean.TRUE.equals(member.isDeputy())).findFirst())
                .orElse(members.get(0));
    }

    private static ObjectNode judgeName(Judiciary judiciary, boolean welsh) {
        ObjectNode node = NODES.objectNode();
        putIfNotBlank(node, "CitizenNameTitle", truncate(localised(welsh, judiciary.judiciaryTitle(), judiciary.welshJudiciaryTitle())));
        if (!isBlank(judiciary.judiciaryForenames())) {
            ArrayNode forenames = node.putArray("CitizenNameForename");
            Arrays.stream(WHITESPACE.split(judiciary.judiciaryForenames().trim())).forEach(forenames::add);
        }
        String surname = isBlank(judiciary.judiciarySurname())
                ? localised(welsh, judiciary.judiciaryName(), judiciary.welshJudiciaryName())
                : judiciary.judiciarySurname();
        node.put("CitizenNameSurname", isBlank(surname) ? JUDGE_NOT_ALLOCATED : truncate(surname));
        putIfNotBlank(node, "CitizenNameSuffix",
                truncate(localised(welsh, judiciary.judiciaryTitleSuffix(), judiciary.welshJudiciaryTitleSuffix())));
        return node;
    }

    // ---- values ----------------------------------------------------------------------------------------------

    /** CaTH needs an integer court house code: the crest court id, else the crest court site id, else 0. */
    private static int courtHouseCode(CrestCourtSite site) {
        return site == null ? 0 : parseInt(site.crestCourtId()).or(() -> parseInt(site.crestCourtSiteId())).orElse(0);
    }

    private static String courtHouseType(String courtType) {
        String upper = courtType == null ? "" : courtType.toUpperCase(Locale.ROOT);
        if (upper.contains("MAGISTRATE")) {
            return "Magistrates Court";
        }
        return upper.contains("YOUTH") ? "Youth Court" : CROWN_COURT;
    }

    /** The last number in the room name ("Court Room 3" gives 3), or 0. */
    static int courtRoomNumber(String courtRoomName) {
        if (courtRoomName == null) {
            return 0;
        }
        Matcher matcher = DIGITS.matcher(courtRoomName);
        String last = null;
        while (matcher.find()) {
            last = matcher.group();
        }
        return parseInt(last).orElse(0);
    }

    /** Listing sends UK wall-clock times without an offset; a time with an offset is converted to UK time. */
    static Optional<LocalTime> startTime(String value) {
        if (isBlank(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime.parse(value).toLocalTime());
        } catch (DateTimeParseException noLocalTime) {
            try {
                return Optional.of(OffsetDateTime.parse(value).atZoneSameInstant(UK).toLocalTime());
            } catch (DateTimeParseException noOffsetTime) {
                return Optional.empty();
            }
        }
    }

    private static Optional<Integer> parseInt(String value) {
        if (isBlank(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static boolean isPublishable(Party party) {
        return party != null && !Boolean.TRUE.equals(party.restrictFromCourtList());
    }

    private static String localised(boolean welsh, String english, String welshValue) {
        return welsh && !isBlank(welshValue) ? welshValue : english;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH).trim() : trimmed;
    }

    private static List<String> nonBlank(String... values) {
        return Arrays.stream(values).filter(value -> !isBlank(value)).map(String::trim).toList();
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list.stream().filter(Objects::nonNull).toList();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void putIfNotBlank(ObjectNode node, String field, String value) {
        if (!isBlank(value)) {
            node.put(field, value);
        }
    }

    private static void putIfPresent(ObjectNode node, String field, ObjectNode value) {
        if (value != null) {
            node.set(field, value);
        }
    }

    private static void putIfNotEmpty(ObjectNode node, String field, ArrayNode value) {
        if (value != null && !value.isEmpty()) {
            node.set(field, value);
        }
    }
}
