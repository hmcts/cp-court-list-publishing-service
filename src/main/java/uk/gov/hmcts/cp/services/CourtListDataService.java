package uk.gov.hmcts.cp.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.config.CourtListPublishingSystemUserConfig;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;
import uk.gov.hmcts.cp.models.CourtCentreData;
import uk.gov.hmcts.cp.models.CourtListPayload;
import uk.gov.hmcts.cp.openapi.model.CourtListType;

/**
 * Returns court list data from listing + reference data only (no progression).
 * Same data shape as progression GET /courtlistdata.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CourtListDataService {

    private static final String OU_CODE = "ouCode";
    private static final String COURT_ID = "courtId";
    private static final String COURT_ID_NUMERIC = "courtIdNumeric";
    private static final String OUCODE_L1_CODE = "oucodeL1Code";
    private static final String OUCODE_L1_NAME = "oucodeL1Name";
    private static final String OUCODE_L3_CODE = "oucodeL3Code";
    private static final String OUCODE_L3_NAME = "oucodeL3Name";
    private static final String OUCODE_L3_WELSH_NAME = "oucodeL3WelshName";
    private static final String ADDRESS1 = "address1";
    private static final String ADDRESS2 = "address2";
    private static final String ADDRESS3 = "address3";
    private static final String ADDRESS4 = "address4";
    private static final String ADDRESS5 = "address5";
    private static final String POSTCODE = "postcode";
    private static final String IS_WELSH = "isWelsh";
    private static final String WELSH_ADDRESS1 = "welshAddress1";
    private static final String WELSH_ADDRESS2 = "welshAddress2";
    private static final String WELSH_ADDRESS3 = "welshAddress3";
    private static final String WELSH_ADDRESS4 = "welshAddress4";
    private static final String WELSH_ADDRESS5 = "welshAddress5";
    private static final String DEFAULT_START_TIME = "defaultStartTime";
    private static final String DEFAULT_DURATION_HRS = "defaultDurationHrs";
    private static final String COURT_LOCATION_CODE = "courtLocationCode";
    private static final String PHONE = "phone";
    private static final String EMAIL = "email";
    // CourtListPayload field names for address/venue
    private static final String COURT_CENTRE_ADDRESS1 = "courtCentreAddress1";
    private static final String COURT_CENTRE_ADDRESS2 = "courtCentreAddress2";
    private static final String COURT_CENTRE_DEFAULT_START_TIME = "courtCentreDefaultStartTime";
    private static final String WELSH_COURT_CENTRE_NAME = "welshCourtCentreName";
    private static final String WELSH_COURT_CENTRE_ADDRESS1 = "welshCourtCentreAddress1";
    private static final String WELSH_COURT_CENTRE_ADDRESS2 = "welshCourtCentreAddress2";

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperConfig.getObjectMapper();

    private final ListingQueryService listingQueryService;
    private final ReferenceDataService referenceDataService;
    private final CourtListPublishingSystemUserConfig systemUserConfig;

    /**
     * Fetches court list data from listing, enriches with ouCode/courtId from reference data,
     * returns JSON string (same shape as progression /courtlistdata).
     * @param currentUserId user ID for listing call (e.g. CJSCPPUID from request).
     * @param systemUserId user ID for reference data call.
     */
    public String getCourtListData(
            CourtListType listId,
            String courtCentreId,
            String courtRoomId,
            String startDate,
            String endDate,
            boolean restricted,
            String currentUserId,
            String systemUserId) {
        String listingJson = listingQueryService.getCourtListPayload(
                listId, courtCentreId, courtRoomId, startDate, endDate, restricted, currentUserId);

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
        if (courtCentreId != null && !courtCentreId.isBlank()) {
            referenceDataService.getCourtCenterDataByCourtCentreId(courtCentreId, systemUserId)
                    .ifPresent(data -> addCourtCentreIds(object, data));
        }
        // Ensure venue address fields (address1, address2) are set for transformation; use court centre when reference data did not provide them
        if (!object.has(ADDRESS1) && object.has(COURT_CENTRE_ADDRESS1)) {
            object.put(ADDRESS1, object.get(COURT_CENTRE_ADDRESS1).asText());
        }
        if (!object.has(ADDRESS2) && object.has(COURT_CENTRE_ADDRESS2)) {
            object.put(ADDRESS2, object.get(COURT_CENTRE_ADDRESS2).asText());
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (Exception e) {
            log.warn("Could not serialize enriched court list data, returning listing payload as-is: {}", e.getMessage());
            return listingJson;
        }
    }

    /**
     * Fetches court list data from listing + reference data and returns as CourtListPayload.
     * Equivalent to progression court list payload for use by CourtListQueryService.
     */
    public CourtListPayload getCourtListPayload(
            CourtListType listId,
            String courtCentreId,
            String startDate,
            String endDate,
            String cjscppuid) {
        String systemUserId = systemUserConfig.getSystemUserId();
        if (systemUserId == null || systemUserId.isBlank()) {
            throw new IllegalStateException("COURTLISTPUBLISHING_SYSTEM_USER_ID is not configured");
        }
        boolean restricted = cjscppuid != null && !cjscppuid.trim().isEmpty();
        String json = getCourtListData(listId, courtCentreId, null, startDate, endDate, restricted, cjscppuid, systemUserId);
        try {
            return OBJECT_MAPPER.readValue(json, CourtListPayload.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize court list data to CourtListPayload: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse court list payload", e);
        }
    }

    private void addCourtCentreIds(ObjectNode object, CourtCentreData data) {
        if (data.getOuCode() != null) {
            object.put(OU_CODE, data.getOuCode());
        }
        if (data.getId() != null) {
            object.put(COURT_ID, data.getId().toString());
        }
        if (data.getCourtIdNumeric() != null) {
            object.put(COURT_ID_NUMERIC, data.getCourtIdNumeric());
        }
        if (data.getOucodeL1Code() != null) {
            object.put(OUCODE_L1_CODE, data.getOucodeL1Code());
        }
        if (data.getOucodeL1Name() != null) {
            object.put(OUCODE_L1_NAME, data.getOucodeL1Name());
        }
        if (data.getOucodeL3Code() != null) {
            object.put(OUCODE_L3_CODE, data.getOucodeL3Code());
        }
        if (data.getOucodeL3Name() != null) {
            object.put(OUCODE_L3_NAME, data.getOucodeL3Name());
        }
        if (data.getOucodeL3WelshName() != null) {
            object.put(OUCODE_L3_WELSH_NAME, data.getOucodeL3WelshName());
        }
        if (data.getAddress1() != null) {
            object.put(ADDRESS1, data.getAddress1());
        }
        if (data.getAddress2() != null) {
            object.put(ADDRESS2, data.getAddress2());
        }
        if (data.getAddress3() != null) {
            object.put(ADDRESS3, data.getAddress3());
        }
        if (data.getAddress4() != null) {
            object.put(ADDRESS4, data.getAddress4());
        }
        if (data.getAddress5() != null) {
            object.put(ADDRESS5, data.getAddress5());
        }
        if (data.getPostcode() != null) {
            object.put(POSTCODE, data.getPostcode());
        }
        if (data.getIsWelsh() != null) {
            object.put(IS_WELSH, data.getIsWelsh());
        }
        if (data.getWelshAddress1() != null) {
            object.put(WELSH_ADDRESS1, data.getWelshAddress1());
        }
        if (data.getWelshAddress2() != null) {
            object.put(WELSH_ADDRESS2, data.getWelshAddress2());
        }
        if (data.getWelshAddress3() != null) {
            object.put(WELSH_ADDRESS3, data.getWelshAddress3());
        }
        if (data.getWelshAddress4() != null) {
            object.put(WELSH_ADDRESS4, data.getWelshAddress4());
        }
        if (data.getWelshAddress5() != null) {
            object.put(WELSH_ADDRESS5, data.getWelshAddress5());
        }
        if (data.getDefaultStartTime() != null) {
            object.put(DEFAULT_START_TIME, data.getDefaultStartTime());
        }
        if (data.getDefaultDurationHrs() != null) {
            object.put(DEFAULT_DURATION_HRS, data.getDefaultDurationHrs());
        }
        if (data.getCourtLocationCode() != null) {
            object.put(COURT_LOCATION_CODE, data.getCourtLocationCode());
        }
        if (data.getPhone() != null) {
            object.put(PHONE, data.getPhone());
        }
        if (data.getEmail() != null) {
            object.put(EMAIL, data.getEmail());
        }
        if (data.getAddress1() != null) {
            object.put(COURT_CENTRE_ADDRESS1, data.getAddress1());
        }
        if (data.getAddress2() != null) {
            object.put(COURT_CENTRE_ADDRESS2, data.getAddress2());
        }
        if (data.getDefaultStartTime() != null) {
            object.put(COURT_CENTRE_DEFAULT_START_TIME, data.getDefaultStartTime());
        }
        if (data.getOucodeL3WelshName() != null) {
            object.put(WELSH_COURT_CENTRE_NAME, data.getOucodeL3WelshName());
        }
        if (data.getWelshAddress1() != null) {
            object.put(WELSH_COURT_CENTRE_ADDRESS1, data.getWelshAddress1());
        }
        if (data.getWelshAddress2() != null) {
            object.put(WELSH_COURT_CENTRE_ADDRESS2, data.getWelshAddress2());
        }
    }
}
