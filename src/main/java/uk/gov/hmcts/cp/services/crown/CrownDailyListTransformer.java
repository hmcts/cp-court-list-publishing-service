package uk.gov.hmcts.cp.services.crown;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds the {@code crown_daily_list} document ({@code DailyList}) for DRAFT and FINAL lists: one court list per
 * court site, each with its sittings (a court room, a judge and the hearings in start time order).
 */
@Component
public class CrownDailyListTransformer extends AbstractCrownListTransformer {

    private static final String SITTING_PRIORITY = "T";

    public CrownDailyListTransformer() {
        this(Clock.systemUTC());
    }

    CrownDailyListTransformer(final Clock clock) {
        super(clock);
    }

    @Override
    public CrownListType type() {
        return CrownListType.DAILY;
    }

    @Override
    public ObjectNode transform(final CrownTransformInput input) {
        final ObjectNode list = MAPPER.createObjectNode();
        list.set("DocumentID", documentId(type()));
        list.set("ListHeader", listHeader(input));
        list.set("CrownCourt", crownCourt(input));

        final ArrayNode courtLists = list.putArray("CourtLists");
        input.payload().path("courtLists").forEach(courtList -> courtLists.add(courtList(input, courtList)));

        final ObjectNode root = MAPPER.createObjectNode();
        root.set(type().rootElement(), list);
        return root;
    }

    private ObjectNode courtList(final CrownTransformInput input, final JsonNode courtList) {
        final ObjectNode node = MAPPER.createObjectNode();
        node.set("CourtHouse", courtHouse(input, courtList.path("crestCourtSite")));

        final List<ObjectNode> sittings = new ArrayList<>();
        courtList.path("sittings").forEach(sitting -> {
            final List<JsonNode> hearings = visibleHearings(sitting);
            if (!hearings.isEmpty()) {
                sittings.add(sitting(sitting, hearings));
            }
        });
        sittings.sort(Comparator.<ObjectNode>comparingInt(s -> s.get("CourtRoomNumber").asInt())
                .thenComparing(s -> s.path("SittingAt").asText()));

        final ArrayNode sittingsNode = node.putArray("Sittings");
        sittings.forEach(sittingsNode::add);
        return node;
    }

    private ObjectNode sitting(final JsonNode sitting, final List<JsonNode> hearings) {
        final LocalDate sittingDate = parseDate(text(sitting, "sittingDate"));
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
        node.putObject("HearingDetails").put("HearingDescription", hearingDescription(hearing));
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
        final ArrayNode defendants = defendants(hearing, sittingDate, false, false);
        if (!defendants.isEmpty()) {
            node.set("Defendants", defendants);
        }
        final ArrayNode respondents = respondents(hearing);
        if (!respondents.isEmpty()) {
            node.set("Respondent", respondents);
        }
        return node;
    }
}
