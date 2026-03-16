package uk.gov.hmcts.cp.services.sjp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;
import uk.gov.hmcts.cp.domain.sjp.SjpListPayload;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transforms SJP list payload to CaTH/PubHub-style JSON (equivalent to SjpPublishingHubTransformer in staging PubHub).
 */
@Component
public class SjpToCathPayloadTransformer {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperConfig.getObjectMapper();

    /**
     * Builds the CaTH payload JSON from the SJP list payload.
     *
     * @param listPayload  SJP list payload (generatedDateAndTime, readyCases)
     * @param documentName e.g. "SJP Public list" or "SJP Press list"
     * @return JSON string to send to CaTH via CourtListPublisher
     */
    public String transform(SjpListPayload listPayload, String documentName) throws JsonProcessingException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("documentName", documentName);
        root.put("publicationDate", listPayload.getGeneratedDateAndTime() != null
                ? listPayload.getGeneratedDateAndTime()
                : "");
        List<Map<String, Object>> courtList = buildCourtList(listPayload.getReadyCases());
        root.put("courtList", courtList);
        return OBJECT_MAPPER.writeValueAsString(root);
    }

    private List<Map<String, Object>> buildCourtList(List<Map<String, Object>> readyCases) {
        if (readyCases == null) {
            return List.of();
        }
        List<Map<String, Object>> courtList = new ArrayList<>();
        for (Map<String, Object> readyCase : readyCases) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("caseUrn", readyCase.get("caseUrn"));
            entry.put("defendantName", readyCase.get("defendantName"));
            entry.put("sjpOffences", readyCase.get("sjpOffences"));
            entry.put("prosecutor", readyCase.get("prosecutor"));
            entry.put("address", readyCase.get("address"));
            entry.put("postcode", readyCase.get("postcode"));
            courtList.add(entry);
        }
        return courtList;
    }
}
