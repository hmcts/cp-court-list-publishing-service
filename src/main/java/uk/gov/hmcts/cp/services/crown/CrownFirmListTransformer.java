package uk.gov.hmcts.cp.services.crown;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Builds the {@code crown_firm_list} document ({@code FirmList}) for FIRM lists: one court list per court site
 * and sitting date, each with its sittings like the daily list. Hearings with no court room or no fixed date go on
 * the reserve list.
 */
@Component
public class CrownFirmListTransformer extends AbstractCrownListTransformer {

    private static final Logger LOG = LoggerFactory.getLogger(CrownFirmListTransformer.class);

    private static final String SITTING_PRIORITY = "F";
    /** The firm list schema accepts only this shape for reserve list case numbers. */
    private static final Pattern RESERVE_CASE_NUMBER = Pattern.compile("^[A-Z][0-9]{8}$");

    public CrownFirmListTransformer() {
        this(Clock.systemUTC());
    }

    CrownFirmListTransformer(final Clock clock) {
        super(clock);
    }

    @Override
    public CrownListType type() {
        return CrownListType.FIRM;
    }

    @Override
    public ObjectNode transform(final CrownTransformInput input) {
        final ObjectNode list = MAPPER.createObjectNode();
        final ObjectNode documentId = documentId(type());
        documentId.put("TimeStamp", now().toString());
        list.set("DocumentID", documentId);
        list.set("ListHeader", listHeader(input));
        list.set("CrownCourt", crownCourt(input));

        final List<DatedCourtList> courtLists = new ArrayList<>();
        final ArrayNode reserveList = MAPPER.createArrayNode();
        final int[] skippedReserveEntries = {0};
        final int[] siteIndex = {0};

        input.payload().path("courtLists").forEach(courtList -> {
            final int site = siteIndex[0]++;
            final ObjectNode courtHouse = courtHouse(input, courtList.path("crestCourtSite"));
            final List<DatedCourtList> thisSite = new ArrayList<>();

            courtList.path("sittings").forEach(sitting -> {
                final List<JsonNode> hearings = visibleHearings(sitting);
                if (hearings.isEmpty()) {
                    return;
                }
                final LocalDate sittingDate = parseDate(text(sitting, "sittingDate"));
                if (isReserve(sitting, sittingDate)) {
                    for (final JsonNode hearing : hearings) {
                        final ObjectNode entry = reserveEntry(hearing, sittingDate);
                        if (entry == null) {
                            skippedReserveEntries[0]++;
                        } else {
                            reserveList.add(entry);
                        }
                    }
                    return;
                }
                DatedCourtList dated = thisSite.stream().filter(c -> c.date.equals(sittingDate)).findFirst().orElse(null);
                if (dated == null) {
                    dated = new DatedCourtList(sittingDate, site, courtHouse.deepCopy());
                    thisSite.add(dated);
                }
                dated.sittings.add(sitting(sitting, hearings, sittingDate));
            });
            courtLists.addAll(thisSite);
        });

        if (skippedReserveEntries[0] > 0) {
            LOG.warn("{} reserve list hearing(s) were left out because their case number does not have the shape the "
                    + "firm list schema requires for reserve list cases", skippedReserveEntries[0]);
        }

        courtLists.sort(Comparator.<DatedCourtList, LocalDate>comparing(c -> c.date).thenComparingInt(c -> c.site));
        final ArrayNode courtListsNode = list.putArray("CourtLists");
        courtLists.forEach(c -> courtListsNode.add(c.toNode()));
        if (!reserveList.isEmpty()) {
            list.set("ReserveList", reserveList);
        }

        final ObjectNode root = MAPPER.createObjectNode();
        root.set(type().rootElement(), list);
        return root;
    }

    private static boolean isReserve(final JsonNode sitting, final LocalDate sittingDate) {
        return sittingDate == null
                || bool(sitting, "weekCommencing")
                || text(sitting, "courtRoomId") == null;
    }

    private ObjectNode sitting(final JsonNode sitting, final List<JsonNode> hearings, final LocalDate sittingDate) {
        final ObjectNode node = MAPPER.createObjectNode();
        node.put("CourtRoomNumber", courtRoomNumber(sitting));
        node.put("SittingAt", sittingAt(hearings));
        node.put("SittingPriority", SITTING_PRIORITY);
        putIfPresent(node, "SittingNote", text(sitting, "sittingNote"));
        node.set("Judiciary", judiciary(sitting));

        final ArrayNode hearingsNode = node.putArray("Hearings");
        int sequence = 1;
        for (final JsonNode hearing : hearings) {
            hearingsNode.add(hearing(hearing, sequence++, sittingDate));
        }
        return node;
    }

    private ObjectNode hearing(final JsonNode hearing, final int sequence, final LocalDate sittingDate) {
        final ObjectNode node = MAPPER.createObjectNode();
        node.put("HearingSequenceNumber", sequence);
        node.set("HearingDetails", hearingDetails(hearing, sittingDate));
        putIfPresent(node, "TimeMarkingNote", timeMarkingNote(hearing));
        final String caseNumber = caseNumber(hearing);
        node.put("CaseNumber", caseNumber);
        node.put("CaseNumberCaTH", caseNumber);
        final ObjectNode prosecution = prosecution(hearing);
        if (prosecution != null) {
            node.set("Prosecution", prosecution);
        }
        final ObjectNode committingCourt = committingCourt(hearing);
        if (committingCourt != null) {
            node.set("CommittingCourt", committingCourt);
        }
        putIfPresent(node, "ListNote", text(hearing, "publicListNote"));
        final ArrayNode defendants = defendants(hearing, sittingDate, false, true);
        if (!defendants.isEmpty()) {
            node.set("Defendants", defendants);
        }
        final ArrayNode respondents = respondents(hearing);
        if (!respondents.isEmpty()) {
            node.set("Respondent", respondents);
        }
        return node;
    }

    private ObjectNode hearingDetails(final JsonNode hearing, final LocalDate sittingDate) {
        final ObjectNode details = MAPPER.createObjectNode();
        details.put("HearingDescription", hearingDescription(hearing));
        final LocalDateTime start = startTime(hearing);
        final LocalDate hearingDate = start != null ? start.toLocalDate() : sittingDate;
        if (hearingDate != null) {
            details.put("HearingDate", hearingDate.toString());
        }
        final LocalDateTime end = endTime(hearing);
        if (end != null && hearingDate != null && end.toLocalDate().isAfter(hearingDate)) {
            details.put("HearingEndDate", end.toLocalDate().toString());
        }
        return details;
    }

    /** @return the reserve list entry, or null when the case number is not one the schema allows there */
    private ObjectNode reserveEntry(final JsonNode hearing, final LocalDate sittingDate) {
        final String caseNumber = caseNumber(hearing);
        if (!RESERVE_CASE_NUMBER.matcher(caseNumber).matches()) {
            return null;
        }
        final ObjectNode node = MAPPER.createObjectNode();
        node.set("HearingDetails", hearingDetails(hearing, sittingDate));
        node.put("CaseNumber", caseNumber);
        node.put("CaseNumberCaTH", caseNumber);
        final ObjectNode prosecution = prosecution(hearing);
        if (prosecution != null) {
            node.set("Prosecution", prosecution);
        }
        final ObjectNode committingCourt = committingCourt(hearing);
        if (committingCourt != null) {
            node.set("CommittingCourt", committingCourt);
        }
        putIfPresent(node, "ListNote", text(hearing, "publicListNote"));
        final ArrayNode defendants = MAPPER.createArrayNode();
        hearing.path("defendants").forEach(defendant -> defendants.add(defendantNameOnly(defendant, hearing)));
        if (!defendants.isEmpty()) {
            node.set("Defendants", defendants);
        }
        final ArrayNode respondents = respondents(hearing);
        if (!respondents.isEmpty()) {
            node.set("Respondent", respondents);
        }
        return node;
    }

    private static final class DatedCourtList {
        private final LocalDate date;
        private final int site;
        private final ObjectNode courtHouse;
        private final List<ObjectNode> sittings = new ArrayList<>();

        private DatedCourtList(final LocalDate date, final int site, final ObjectNode courtHouse) {
            this.date = date;
            this.site = site;
            this.courtHouse = courtHouse;
        }

        private ObjectNode toNode() {
            sittings.sort(Comparator.<ObjectNode>comparingInt(s -> s.get("CourtRoomNumber").asInt())
                    .thenComparing(s -> s.path("SittingAt").asText()));
            final ObjectNode node = MAPPER.createObjectNode();
            node.put("SittingDate", date.toString());
            node.set("CourtHouse", courtHouse);
            final ArrayNode sittingsNode = node.putArray("Sittings");
            sittings.forEach(sittingsNode::add);
            return node;
        }
    }
}
