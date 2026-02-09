package uk.gov.hmcts.cp.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;
import uk.gov.hmcts.cp.models.CourtCentreData;
import uk.gov.hmcts.cp.openapi.model.CourtListType;

/**
 * Returns court list data from listing + reference data only (no progression).
 * Same data shape as progression GET /courtlistdata.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CourtListDataService {

    private static final String COURT_CENTRE_NAME = "courtCentreName";
    private static final String OU_CODE = "ouCode";
    private static final String COURT_ID = "courtId";

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperConfig.getObjectMapper();

    private final ListingQueryService listingQueryService;
    private final ReferenceDataService referenceDataService;

    /**
     * Fetches court list data from listing, enriches with ouCode/courtId from reference data,
     * returns JSON string (same shape as progression /courtlistdata).
     */
    public String getCourtListData(
            CourtListType listId,
            String courtCentreId,
            String courtRoomId,
            String startDate,
            String endDate,
            boolean restricted) {
        String listingJson = listingQueryService.getCourtListPayload(
                listId, courtCentreId, courtRoomId, startDate, endDate, restricted);

        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(listingJson);
        } catch (Exception e) {
            log.warn("Could not parse listing response as JSON: {}", e.getMessage());
            return listingJson;
        }
        if (!root.isObject()) {
            return listingJson;
        }
        ObjectNode object = (ObjectNode) root;
        String courtCentreName = object.has(COURT_CENTRE_NAME) ? object.get(COURT_CENTRE_NAME).asText("") : "";
        if (!courtCentreName.isBlank()) {
            referenceDataService.getCourtCenterDataByCourtName(courtCentreName)
                    .ifPresent(data -> addCourtCentreIds(object, data));
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (Exception e) {
            log.warn("Could not serialize enriched court list data, returning listing payload as-is: {}", e.getMessage());
            return listingJson;
        }
    }

    private void addCourtCentreIds(ObjectNode object, CourtCentreData data) {
        if (data.getOuCode() != null) {
            object.put(OU_CODE, data.getOuCode());
        }
        if (data.getId() != null) {
            object.put(COURT_ID, data.getId().toString());
        }
    }
}
