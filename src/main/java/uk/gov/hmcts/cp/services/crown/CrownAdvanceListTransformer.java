package uk.gov.hmcts.cp.services.crown;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds the {@code crown_advance_list} document ({@code WarnedList}) for WARN lists. Each court list holds the
 * cases warned for the week, in two groups: those with a fixed date and those without one (listed for the week
 * only). Within a group the cases are grouped by hearing type.
 */
@Component
public class CrownAdvanceListTransformer extends AbstractCrownListTransformer {

    public CrownAdvanceListTransformer() {
        this(Clock.systemUTC());
    }

    CrownAdvanceListTransformer(final Clock clock) {
        super(clock);
    }

    @Override
    public CrownListType type() {
        return CrownListType.ADVANCE;
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
        // hearing type description -> fixed date -> cases, and hearing type description -> cases
        final Map<String, Map<LocalDate, ArrayNode>> withFixedDate = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        final Map<String, ArrayNode> withoutFixedDate = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        courtList.path("sittings").forEach(sitting -> {
            final LocalDate sittingDate = parseDate(text(sitting, "sittingDate"));
            for (final JsonNode hearing : visibleHearings(sitting)) {
                final boolean fixed = sittingDate != null && !bool(sitting, "weekCommencing") && !bool(hearing, "weekCommencing");
                final String group = hearingDescription(hearing);
                if (fixed) {
                    withFixedDate.computeIfAbsent(group, key -> new TreeMap<>())
                            .computeIfAbsent(sittingDate, key -> MAPPER.createArrayNode())
                            .add(caseNode(hearing, sittingDate, sittingDate));
                } else {
                    withoutFixedDate.computeIfAbsent(group, key -> MAPPER.createArrayNode())
                            .add(caseNode(hearing, input.startDate(), input.startDate()));
                }
            }
        });

        final ObjectNode node = MAPPER.createObjectNode();
        node.set("CourtHouse", courtHouse(input, courtList.path("crestCourtSite")));

        if (!withFixedDate.isEmpty()) {
            final ArrayNode fixedGroups = node.putArray("WithFixedDate");
            withFixedDate.values().forEach(byDate -> {
                final ArrayNode fixtures = MAPPER.createArrayNode();
                byDate.forEach((date, cases) -> {
                    final ObjectNode fixture = MAPPER.createObjectNode();
                    fixture.put("FixedDate", date.toString());
                    fixture.set("Cases", cases);
                    fixtures.add(fixture);
                });
                fixedGroups.add(MAPPER.createObjectNode().set("Fixture", fixtures));
            });
        }
        if (!withoutFixedDate.isEmpty()) {
            final ArrayNode floatingGroups = node.putArray("WithoutFixedDate");
            withoutFixedDate.values().forEach(cases -> {
                final ObjectNode fixture = MAPPER.createObjectNode();
                fixture.set("Cases", cases);
                floatingGroups.add(MAPPER.createObjectNode().set("Fixture", MAPPER.createArrayNode().add(fixture)));
            });
        }
        return node;
    }

    private ObjectNode caseNode(final JsonNode hearing, final LocalDate hearingDate, final LocalDate ageAt) {
        final ObjectNode node = MAPPER.createObjectNode();
        final String caseNumber = caseNumber(hearing);
        node.put("CaseNumber", caseNumber);
        node.put("CaseNumberCaTH", caseNumber);

        final ObjectNode origin = originatingCourt(hearing);
        if (origin != null) {
            node.putObject("CaseArrivedFrom").set("OriginatingCourt", origin);
        }

        final ObjectNode hearingNode = MAPPER.createObjectNode();
        hearingNode.put("HearingDescription", hearingDescription(hearing));
        if (hearingDate != null) {
            hearingNode.put("HearingDate", hearingDate.toString());
        }
        putIfPresent(hearingNode, "ListNote", text(hearing, "publicListNote"));
        node.putArray("Hearing").add(hearingNode);

        node.set("Defendants", defendants(hearing, ageAt, true, true));

        final ObjectNode prosecution = prosecution(hearing);
        if (prosecution != null) {
            node.set("Prosecution", prosecution);
        }
        final ArrayNode linked = linkedCases(hearing);
        if (!linked.isEmpty()) {
            node.set("LinkedCases", linked);
        }
        return node;
    }
}
