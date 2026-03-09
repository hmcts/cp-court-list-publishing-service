package uk.gov.hmcts.cp.services.sjp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.api.sjp.SjpListPayload;

import java.util.List;
import java.util.Map;

/**
 * Transforms SJP list payload (same shape as PubHub event listPayload) into CaTH/PubhubMaster JSON.
 * Matches SjpPublishingHubTransformer in cpp-context-staging-pubhub: document + courtLists with
 * courtHouse/courtRoom/session/sittings/hearing (party, offence, case).
 */
@Component
public class SjpToCathPayloadTransformer {

    private static final Logger LOG = LoggerFactory.getLogger(SjpToCathPayloadTransformer.class);
    private static final String VERSION = "1.0";

    private static final String PROSECUTOR_NAME = "prosecutorName";
    private static final String LEGAL_ENTITY_NAME = "legalEntityName";
    private static final String DEFENDANT_NAME = "defendantName";
    private static final String FIRST_NAME = "firstName";
    private static final String LAST_NAME = "lastName";
    private static final String TITLE = "title";
    private static final String DATE_OF_BIRTH = "dateOfBirth";
    private static final String AGE = "age";
    private static final String ADDRESS_LINE_1 = "addressLine1";
    private static final String ADDRESS_LINE_2 = "addressLine2";
    private static final String ADDRESS_LINE_3 = "addressLine3";
    private static final String TOWN = "town";
    private static final String COUNTRY = "county";
    private static final String POSTCODE = "postcode";
    private static final String CASE_URN = "caseUrn";
    private static final String SJP_OFFENCES = "sjpOffences";
    private static final String OFFENCE_TITLE = "title";
    private static final String OFFENCE_WORDING = "wording";
    private static final String REPORTING_RESTRICTION = "reportingRestriction";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Builds CaTH payload JSON string from SJP listPayload and document display name.
     * @param listPayload SJP list payload (generatedDateAndTime, readyCases)
     * @param documentName e.g. "SJP Public list" or "SJP Press list"
     * @param isPressList true to include reportingRestriction on offences
     */
    public String transform(SjpListPayload listPayload, String documentName, boolean isPressList) {
        ObjectNode root = objectMapper.createObjectNode();

        ObjectNode document = root.putObject("document");
        document.put("documentName", documentName);
        document.put("publicationDate", listPayload.getGeneratedDateAndTime() != null ? listPayload.getGeneratedDateAndTime() : "");
        document.put("version", VERSION);

        ArrayNode courtLists = root.putArray("courtLists");
        ObjectNode courtList = courtLists.addObject();
        ObjectNode courtHouse = courtList.putObject("courtHouse");
        ArrayNode courtRooms = courtHouse.putArray("courtRoom");

        ObjectNode courtRoom = courtRooms.addObject();
        ArrayNode sessions = courtRoom.putArray("session");
        ObjectNode session = sessions.addObject();
        ArrayNode sittings = session.putArray("sittings");
        ObjectNode sitting = sittings.addObject();
        ArrayNode hearings = sitting.putArray("hearing");

        List<Map<String, Object>> readyCases = listPayload.getReadyCases();
        if (readyCases != null) {
            for (Map<String, Object> readyCase : readyCases) {
                hearings.add(buildHearing(readyCase, isPressList));
            }
        }

        String json = root.toString();
        if (LOG.isDebugEnabled()) {
            LOG.debug("SJP CaTH payload built, documentName={}, hearings={}", documentName, hearings.size());
        }
        return json;
    }

    private ObjectNode buildHearing(Map<String, Object> readyCase, boolean isPressList) {
        ObjectNode hearing = objectMapper.createObjectNode();
        hearing.set("party", buildParties(readyCase));
        hearing.set("offence", buildOffences(readyCase, isPressList));
        String caseUrn = getStr(readyCase, CASE_URN);
        if (caseUrn != null && !caseUrn.isEmpty()) {
            ArrayNode caseArray = hearing.putArray("case");
            ObjectNode c = caseArray.addObject();
            c.put("caseUrn", caseUrn);
        }
        return hearing;
    }

    private ArrayNode buildParties(Map<String, Object> readyCase) {
        ArrayNode parties = objectMapper.createArrayNode();
        if (getStr(readyCase, PROSECUTOR_NAME) != null) {
            parties.add(buildProsecutorParty(readyCase));
        }
        if (getStr(readyCase, LEGAL_ENTITY_NAME) != null) {
            parties.add(buildOrganisationParty(readyCase));
        }
        if (getStr(readyCase, DEFENDANT_NAME) != null || getStr(readyCase, FIRST_NAME) != null || getStr(readyCase, LAST_NAME) != null) {
            parties.add(buildIndividualParty(readyCase));
        }
        return parties;
    }

    private ObjectNode buildProsecutorParty(Map<String, Object> readyCase) {
        ObjectNode party = objectMapper.createObjectNode();
        party.put("partyRole", "PROSECUTOR");
        ObjectNode org = party.putObject("organisationDetails");
        org.put("organisationName", getStr(readyCase, PROSECUTOR_NAME));
        return party;
    }

    private ObjectNode buildOrganisationParty(Map<String, Object> readyCase) {
        ObjectNode party = objectMapper.createObjectNode();
        party.put("partyRole", "ACCUSED");
        ObjectNode org = party.putObject("organisationDetails");
        org.put("organisationName", getStr(readyCase, LEGAL_ENTITY_NAME));
        ObjectNode addr = org.putObject("organisationAddress");
        addAddressLines(addr, readyCase);
        return party;
    }

    private ObjectNode buildIndividualParty(Map<String, Object> readyCase) {
        ObjectNode party = objectMapper.createObjectNode();
        party.put("partyRole", "ACCUSED");
        ObjectNode ind = party.putObject("individualDetails");
        ind.put("title", getStr(readyCase, TITLE));
        ind.put("individualForenames", getStr(readyCase, FIRST_NAME));
        ind.put("individualSurname", getStr(readyCase, LAST_NAME));
        ind.put("dateOfBirth", getStr(readyCase, DATE_OF_BIRTH));
        String age = getStr(readyCase, AGE);
        if (age != null && !age.isEmpty()) {
            try {
                ind.put("age", Integer.parseInt(age));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        ObjectNode addr = ind.putObject("address");
        addAddressLines(addr, readyCase);
        return party;
    }

    private void addAddressLines(ObjectNode address, Map<String, Object> readyCase) {
        ArrayNode lines = address.putArray("line");
        addLine(lines, getStr(readyCase, ADDRESS_LINE_1));
        addLine(lines, getStr(readyCase, ADDRESS_LINE_2));
        addLine(lines, getStr(readyCase, ADDRESS_LINE_3));
        address.put("town", getStr(readyCase, TOWN));
        address.put("county", getStr(readyCase, COUNTRY));
        address.put("postCode", getStr(readyCase, POSTCODE));
    }

    private void addLine(ArrayNode lines, String value) {
        if (value != null && !value.isEmpty()) {
            lines.add(value);
        }
    }

    @SuppressWarnings("unchecked")
    private ArrayNode buildOffences(Map<String, Object> readyCase, boolean isPressList) {
        ArrayNode offences = objectMapper.createArrayNode();
        Object sjpOffences = readyCase.get(SJP_OFFENCES);
        if (!(sjpOffences instanceof List)) {
            return offences;
        }
        for (Object item : (List<?>) sjpOffences) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<String, Object> off = (Map<String, Object>) item;
            ObjectNode o = objectMapper.createObjectNode();
            o.put("offenceTitle", getStr(off, OFFENCE_TITLE));
            o.put("offenceWording", getStr(off, OFFENCE_WORDING));
            if (isPressList && off.get(REPORTING_RESTRICTION) instanceof Boolean) {
                o.put("reportingRestriction", (Boolean) off.get(REPORTING_RESTRICTION));
            }
            offences.add(o);
        }
        return offences;
    }

    private static String getStr(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString().trim() : null;
    }
}
