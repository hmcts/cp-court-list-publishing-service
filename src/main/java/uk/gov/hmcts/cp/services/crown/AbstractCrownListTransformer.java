package uk.gov.hmcts.cp.services.crown;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;
import uk.gov.hmcts.cp.models.CourtCentreData;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared building blocks for turning the court list that the listing context returns for the Crown media type
 * ({@code application/vnd.listing.search.crown.list.payload+json}) into the three CaTH Crown documents.
 * <p>
 * The listing payload is the daily list payload plus linked cases, sitting notes, judge name parts and further court
 * centre address details. Values are taken from it where it has them. Values it cannot supply are left out when the
 * schema makes them optional; the few the schema requires use a stated placeholder (see the constants) and are
 * logged.
 */
abstract class AbstractCrownListTransformer implements CrownListTransformer {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractCrownListTransformer.class);

    protected static final ObjectMapper MAPPER = ObjectMapperConfig.getObjectMapper();

    static final String COURT_HOUSE_TYPE = "Crown Court";
    static final String COMMITTING_COURT_HOUSE_TYPE = "Magistrates Court";
    static final String VERSION = "NOT VERSIONED";
    static final String MASK = "******";
    /** The schema requires a judge surname on every sitting; a sitting with no judiciary yet gets this text. */
    static final String UNALLOCATED_JUDGE = "To be allocated";
    /** A defendant name needs a surname; one the listing has neither a last name nor an organisation name for gets this. */
    static final String UNKNOWN_SURNAME = "Unknown";
    static final String CPS = "Crown Prosecution Service";
    static final String OTHER_PROSECUTOR = "Other Prosecutor";

    private static final int MAX_NAME_PART = 35;
    private static final int MAX_REQUESTED_NAME = 70;
    private static final int MAX_ADDRESS_LINES = 5;
    private static final int MAX_JUSTICES = 4;
    private static final Pattern DIGITS = Pattern.compile("(\\d+)");
    private static final DateTimeFormatter SITTING_AT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter TIME_MARKING = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final Clock clock;

    protected AbstractCrownListTransformer(final Clock clock) {
        this.clock = clock;
    }

    // ---------------------------------------------------------------------------------------------------------
    // header and court
    // ---------------------------------------------------------------------------------------------------------

    protected ObjectNode documentId(final CrownListType type) {
        final ObjectNode node = MAPPER.createObjectNode();
        node.put("UniqueID", UUID.randomUUID().toString());
        node.put("DocumentType", type.documentType());
        return node;
    }

    protected ObjectNode listHeader(final CrownTransformInput input) {
        final ObjectNode node = MAPPER.createObjectNode();
        node.put("StartDate", input.startDate().toString());
        node.put("EndDate", input.endDate().toString());
        node.put("Version", VERSION);
        node.put("PublishedTime", now().toString());
        return node;
    }

    protected Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    protected ObjectNode crownCourt(final CrownTransformInput input) {
        final JsonNode payload = input.payload();
        final CourtCentreData centre = input.courtCentre();
        final ObjectNode node = MAPPER.createObjectNode();
        node.put("CourtHouseType", COURT_HOUSE_TYPE);
        node.put("CourtHouseCode", courtHouseCode(centre == null ? null : centre.getCourtIdNumeric(), firstSite(payload)));
        node.put("CourtHouseName", firstNonBlank(centre == null ? null : centre.getOucodeL3Name(),
                text(payload, "courtCentreName"), COURT_HOUSE_TYPE));
        putCourtAddress(node, input, firstSite(payload));
        return node;
    }

    protected ObjectNode courtHouse(final CrownTransformInput input, final JsonNode site) {
        final CourtCentreData centre = input.courtCentre();
        final ObjectNode node = MAPPER.createObjectNode();
        node.put("CourtHouseType", COURT_HOUSE_TYPE);
        node.put("CourtHouseCode", courtHouseCode(text(site, "crestCourtSiteId"), site));
        putIfPresent(node, "CourtHouseShortName", text(site, "crestCourtShortName"));
        node.put("CourtHouseName", firstNonBlank(text(site, "crestCourtSiteName"), text(site, "crestCourtFullName"),
                centre == null ? null : centre.getOucodeL3Name(), text(input.payload(), "courtCentreName"), COURT_HOUSE_TYPE));
        putCourtAddress(node, input, site);
        return node;
    }

    private void putCourtAddress(final ObjectNode node, final CrownTransformInput input, final JsonNode site) {
        final CourtCentreData centre = input.courtCentre();
        final List<String> lines = new ArrayList<>();
        String postcode = null;
        if (centre != null) {
            addIfNotBlank(lines, centre.getAddress1(), centre.getAddress2(), centre.getAddress3(), centre.getAddress4(), centre.getAddress5());
            postcode = centre.getPostcode();
        }
        if (lines.isEmpty() && site != null) {
            addIfNotBlank(lines, text(site, "courtCentreAddress1"), text(site, "courtCentreAddress2"), text(site, "courtCentreAddress3"),
                    text(site, "courtCentreAddress4"), text(site, "courtCentreAddress5"));
            postcode = firstNonBlank(postcode, text(site, "courtCentrePostcode"));
        }
        final ObjectNode address = address(lines, postcode);
        if (address != null) {
            node.set("CourtHouseAddress", address);
        }
        if (centre != null) {
            putIfPresent(node, "CourtHouseTelephone", centre.getPhone());
        }
    }

    protected ObjectNode address(final List<String> lines, final String postcode) {
        final List<String> kept = lines.stream().filter(line -> line != null && !line.isBlank()).limit(MAX_ADDRESS_LINES).toList();
        if (kept.isEmpty() && (postcode == null || postcode.isBlank())) {
            return null;
        }
        final ObjectNode node = MAPPER.createObjectNode();
        if (!kept.isEmpty()) {
            final ArrayNode array = node.putArray("Line");
            kept.forEach(array::add);
        }
        putIfPresent(node, "PostCode", postcode);
        return node;
    }

    private int courtHouseCode(final String preferred, final JsonNode site) {
        for (final String candidate : new String[] {preferred, text(site, "crestCourtSiteId"), text(site, "crestCourtId")}) {
            final Integer code = parseInt(candidate);
            if (code != null) {
                return code;
            }
        }
        LOG.warn("No numeric court house code found; using 0");
        return 0;
    }

    private JsonNode firstSite(final JsonNode payload) {
        final JsonNode courtLists = payload.path("courtLists");
        return courtLists.isArray() && !courtLists.isEmpty() ? courtLists.get(0).path("crestCourtSite") : null;
    }

    // ---------------------------------------------------------------------------------------------------------
    // sittings
    // ---------------------------------------------------------------------------------------------------------

    /** The hearings that may appear on a list: those the court has not restricted from it, in start time order. */
    protected List<JsonNode> visibleHearings(final JsonNode sitting) {
        final List<JsonNode> hearings = new ArrayList<>();
        sitting.path("hearings").forEach(hearing -> {
            if (!bool(hearing, "restrictFromCourtList")) {
                hearings.add(hearing);
            }
        });
        hearings.sort(Comparator.comparing(hearing -> startTime(hearing) == null ? LocalDateTime.MAX : startTime(hearing)));
        return hearings;
    }

    protected int courtRoomNumber(final JsonNode sitting) {
        final String name = text(sitting, "courtRoomName");
        if (name != null) {
            final Matcher matcher = DIGITS.matcher(name);
            if (matcher.find()) {
                final Integer number = parseInt(matcher.group(1));
                if (number != null) {
                    return number;
                }
            }
        }
        LOG.warn("Court room name '{}' has no number; using 0 as the court room number", name);
        return 0;
    }

    protected String sittingAt(final List<JsonNode> hearings) {
        return hearings.stream()
                .map(this::startTime)
                .filter(time -> time != null)
                .min(Comparator.naturalOrder())
                .map(time -> time.toLocalTime().format(SITTING_AT))
                .orElseGet(() -> LocalTime.MIDNIGHT.format(SITTING_AT));
    }

    protected ObjectNode judiciary(final JsonNode sitting) {
        final List<JsonNode> entries = new ArrayList<>();
        sitting.path("judiciary").forEach(entries::add);
        final ObjectNode node = MAPPER.createObjectNode();
        if (entries.isEmpty()) {
            node.set("Judge", citizenName(null, null, UNALLOCATED_JUDGE, null, null));
            return node;
        }
        JsonNode judge = entries.get(0);
        for (final JsonNode entry : entries) {
            if (bool(entry, "isBenchChairman")) {
                judge = entry;
                break;
            }
        }
        node.set("Judge", judiciaryName(judge));
        final ArrayNode justices = MAPPER.createArrayNode();
        for (final JsonNode entry : entries) {
            if (entry != judge && justices.size() < MAX_JUSTICES) {
                justices.add(judiciaryName(entry));
            }
        }
        if (!justices.isEmpty()) {
            node.set("Justice", justices);
        }
        return node;
    }

    private ObjectNode judiciaryName(final JsonNode judiciary) {
        final String fullName = text(judiciary, "judiciaryName");
        final String surname = firstNonBlank(text(judiciary, "judiciarySurname"), lastWord(fullName), UNALLOCATED_JUDGE);
        return citizenName(text(judiciary, "judiciaryTitle"), text(judiciary, "judiciaryForenames"), surname,
                text(judiciary, "judiciaryTitleSuffix"), fullName);
    }

    // ---------------------------------------------------------------------------------------------------------
    // hearings, cases, prosecution, committing court
    // ---------------------------------------------------------------------------------------------------------

    protected String caseNumber(final JsonNode hearing) {
        return firstNonBlank(text(hearing.path("caseIdentifier"), "caseReference"), text(hearing, "applicationReference"), "UNKNOWN");
    }

    protected String hearingDescription(final JsonNode hearing) {
        return firstNonBlank(text(hearing.path("hearingType"), "description"), "Hearing");
    }

    protected String timeMarkingNote(final JsonNode hearing) {
        final LocalDateTime start = startTime(hearing);
        if (start == null || bool(hearing, "weekCommencing")) {
            return null;
        }
        return start.toLocalTime().format(TIME_MARKING);
    }

    protected ObjectNode prosecution(final JsonNode hearing) {
        final String organisation = text(hearing.path("prosecutor"), "organisationName");
        if (organisation == null) {
            return null;
        }
        final String authorityCode = text(hearing.path("caseIdentifier"), "authorityCode");
        final boolean cps = "CPS".equalsIgnoreCase(authorityCode) || organisation.toLowerCase(Locale.ROOT).contains("crown prosecution service");
        final ObjectNode node = MAPPER.createObjectNode();
        node.putObject("ProsecutingOrganisation").put("OrganisationName", organisation);
        node.put("ProsecutingAuthority", cps ? CPS : OTHER_PROSECUTOR);
        return node;
    }

    /** The court the case came from, taken from the offences' committing court when it has a numeric code and a name. */
    protected ObjectNode originatingCourt(final JsonNode hearing) {
        for (final JsonNode defendant : hearing.path("defendants")) {
            for (final JsonNode offence : defendant.path("offences")) {
                final JsonNode court = offence.path("committingCourt");
                final String name = text(court, "courtHouseName");
                final Integer code = parseInt(text(court, "courtHouseCode"));
                if (name != null && code != null) {
                    final ObjectNode node = MAPPER.createObjectNode();
                    node.put("CourtHouseCode", code);
                    node.put("CourtHouseName", name);
                    return node;
                }
            }
        }
        return null;
    }

    protected ObjectNode committingCourt(final JsonNode hearing) {
        final ObjectNode origin = originatingCourt(hearing);
        if (origin == null) {
            return null;
        }
        final ObjectNode node = MAPPER.createObjectNode();
        node.put("CourtHouseType", COMMITTING_COURT_HOUSE_TYPE);
        node.set("CourtHouseCode", origin.get("CourtHouseCode"));
        node.set("CourtHouseName", origin.get("CourtHouseName"));
        return node;
    }

    protected ArrayNode respondents(final JsonNode hearing) {
        final ArrayNode node = MAPPER.createArrayNode();
        hearing.path("respondents").forEach(respondent -> {
            if (!bool(respondent, "restrictFromCourtList")) {
                final String name = (orEmpty(text(respondent, "firstName")) + " " + orEmpty(text(respondent, "lastName"))).trim();
                if (!name.isEmpty()) {
                    node.add(name);
                }
            }
        });
        return node;
    }

    protected ArrayNode linkedCases(final JsonNode hearing) {
        final ArrayNode node = MAPPER.createArrayNode();
        hearing.path("linkedCases").forEach(linked -> {
            final String urn = text(linked, "caseUrn");
            if (urn != null) {
                node.add(MAPPER.createObjectNode().put("CaseNumber", urn));
            }
        });
        return node;
    }

    // ---------------------------------------------------------------------------------------------------------
    // defendants
    // ---------------------------------------------------------------------------------------------------------

    /**
     * @param withCustodyStatus  advance list only
     * @param withCounsel        advance and firm lists
     */
    protected ObjectNode defendant(final JsonNode defendant, final JsonNode hearing, final LocalDate ageAt,
                                   final boolean withCustodyStatus, final boolean withCounsel) {
        final ObjectNode node = MAPPER.createObjectNode();
        node.set("PersonalDetails", personalDetails(defendant, ageAt));
        node.put("URN", caseNumber(hearing));
        if (withCustodyStatus) {
            putIfPresent(node, "CustodyStatus", custodyStatus(defendant.path("bailStatus")));
        }
        final String defenceOrganisation = text(defendant, "defenceOrganisation");
        if (withCounsel && defenceOrganisation != null) {
            final ObjectNode organisation = MAPPER.createObjectNode();
            organisation.putObject("Organisation").put("OrganisationName", defenceOrganisation);
            final ObjectNode solicitor = MAPPER.createObjectNode();
            solicitor.set("Party", organisation);
            final ObjectNode counsel = MAPPER.createObjectNode();
            counsel.putArray("Solicitor").add(solicitor);
            node.putArray("Counsel").add(counsel);
        }
        final ArrayNode charges = charges(defendant);
        if (!charges.isEmpty()) {
            node.set("Charges", charges);
        }
        return node;
    }

    /** Name and masking only: what a reserve list entry carries for a defendant. */
    protected ObjectNode defendantNameOnly(final JsonNode defendant, final JsonNode hearing) {
        final ObjectNode details = MAPPER.createObjectNode();
        final boolean masked = bool(defendant, "restrictFromCourtList");
        details.set("Name", defendantName(defendant, masked));
        if (masked) {
            details.put("MaskedName", MASK);
        }
        details.put("IsMasked", masked ? "yes" : "no");
        final ObjectNode node = MAPPER.createObjectNode();
        node.set("PersonalDetails", details);
        node.put("URN", caseNumber(hearing));
        return node;
    }

    private ObjectNode personalDetails(final JsonNode defendant, final LocalDate ageAt) {
        final boolean masked = bool(defendant, "restrictFromCourtList");
        final ObjectNode details = MAPPER.createObjectNode();
        details.set("Name", defendantName(defendant, masked));
        if (masked) {
            details.put("MaskedName", MASK);
            details.put("IsMasked", "yes");
            return details;
        }
        details.put("IsMasked", "no");
        final LocalDate birthDate = parseDate(text(defendant, "dateOfBirth"));
        if (birthDate != null) {
            details.putObject("DateOfBirth").put("BirthDate", birthDate.toString());
            if (ageAt != null && !birthDate.isAfter(ageAt)) {
                details.put("Age", Period.between(birthDate, ageAt).getYears());
            }
        }
        final JsonNode address = defendant.path("address");
        final List<String> lines = new ArrayList<>();
        addIfNotBlank(lines, text(address, "address1"), text(address, "address2"), text(address, "address3"),
                text(address, "address4"), text(address, "address5"));
        final ObjectNode addressNode = address(lines, text(address, "postcode"));
        if (addressNode != null) {
            details.set("Address", addressNode);
        }
        putIfPresent(details, "Nationality", text(defendant, "nationalityDescription"));
        return details;
    }

    private ObjectNode defendantName(final JsonNode defendant, final boolean masked) {
        if (masked) {
            return citizenName(null, MASK, MASK, null, MASK);
        }
        final String first = text(defendant, "firstName");
        final String last = firstNonBlank(text(defendant, "lastName"), text(defendant, "organisationName"), UNKNOWN_SURNAME);
        final String requested = ((first == null ? "" : first + " ") + last).trim();
        return citizenName(null, first, last, null, requested);
    }

    private ArrayNode charges(final JsonNode defendant) {
        final ArrayNode charges = MAPPER.createArrayNode();
        defendant.path("offences").forEach(offence -> {
            if (bool(offence, "restrictFromCourtList")) {
                return;
            }
            final ObjectNode charge = MAPPER.createObjectNode();
            charge.put("OffenceStatement", firstNonBlank(text(offence.path("statementOfOffence"), "title"), text(offence, "offenceWording"), "Offence"));
            final JsonNode count = offence.path("count");
            if (count.isInt() || count.isLong()) {
                charge.put("IndictmentCountNumber", count.asInt());
            }
            putIfPresent(charge, "CJSoffenceCode", text(offence, "offenceCode"));
            charges.add(charge);
        });
        return charges;
    }

    /** The schema allows five custody statuses; the listing's bail status is mapped on its description. */
    protected String custodyStatus(final JsonNode bailStatus) {
        final String text = text(bailStatus, "description");
        if (text == null) {
            return null;
        }
        final String description = text.toLowerCase(Locale.ROOT);
        // "remanded in custody" and "custody or remanded into custody" are custody; "remanded on bail" is bail
        if (description.contains("custody")) {
            return "In custody";
        }
        if (description.contains("bail")) {
            return "On bail";
        }
        if (description.contains("remand")) {
            return "On remand";
        }
        if (description.contains("care")) {
            return "In care";
        }
        return "Not applicable";
    }

    protected ArrayNode defendants(final JsonNode hearing, final LocalDate ageAt, final boolean withCustodyStatus, final boolean withCounsel) {
        final ArrayNode defendants = MAPPER.createArrayNode();
        hearing.path("defendants").forEach(defendant -> defendants.add(defendant(defendant, hearing, ageAt, withCustodyStatus, withCounsel)));
        return defendants;
    }

    // ---------------------------------------------------------------------------------------------------------
    // small helpers
    // ---------------------------------------------------------------------------------------------------------

    protected ObjectNode citizenName(final String title, final String forenames, final String surname,
                                     final String suffix, final String requestedName) {
        final ObjectNode node = MAPPER.createObjectNode();
        putIfPresent(node, "CitizenNameTitle", truncate(title, MAX_NAME_PART));
        if (forenames != null && !forenames.isBlank()) {
            node.putArray("CitizenNameForename").add(forenames.trim());
        }
        putIfPresent(node, "CitizenNameSurname", truncate(surname, MAX_NAME_PART));
        putIfPresent(node, "CitizenNameSuffix", truncate(suffix, MAX_NAME_PART));
        putIfPresent(node, "CitizenNameRequestedName", truncate(requestedName, MAX_REQUESTED_NAME));
        return node;
    }

    protected LocalDateTime startTime(final JsonNode hearing) {
        return parseDateTime(text(hearing, "startTime"));
    }

    protected LocalDateTime endTime(final JsonNode hearing) {
        return parseDateTime(text(hearing, "endTime"));
    }

    static LocalDateTime parseDateTime(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (final DateTimeParseException e) {
            try {
                return OffsetDateTime.parse(value).toLocalDateTime();
            } catch (final DateTimeParseException ignored) {
                return null;
            }
        }
    }

    static LocalDate parseDate(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (final DateTimeParseException e) {
            return null;
        }
    }

    static Integer parseInt(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    static String text(final JsonNode node, final String field) {
        if (node == null) {
            return null;
        }
        final JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        final String text = value.asText();
        return text.isBlank() ? null : text;
    }

    private static String orEmpty(final String value) {
        return value == null ? "" : value;
    }

    static boolean bool(final JsonNode node, final String field) {
        return node != null && node.path(field).asBoolean(false);
    }

    static String firstNonBlank(final String... values) {
        for (final String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    static void putIfPresent(final ObjectNode node, final String field, final String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    private static void addIfNotBlank(final List<String> target, final String... values) {
        for (final String value : values) {
            if (value != null && !value.isBlank()) {
                target.add(value);
            }
        }
    }

    private static String truncate(final String value, final int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String lastWord(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        final String[] words = value.trim().split("\\s+");
        return words[words.length - 1];
    }
}
