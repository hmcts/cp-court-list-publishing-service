package uk.gov.hmcts.cp.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;
import uk.gov.hmcts.cp.models.CourtListPayload;
import uk.gov.hmcts.cp.openapi.model.CourtListType;

/**
 * Returns court list data from progression GET /courtlistdata (payload already includes ouCode, courtId, LJA etc.).
 * No reference data enrichment needed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CourtListDataService {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperConfig.getObjectMapper();

    private final ProgressionQueryService progressionQueryService;

    /**
     * Fetches court list data from progression /courtlistdata and returns it as JSON string.
     *
     * @param currentUserId user ID for progression call (e.g. CJSCPPUID from request).
     */
    public String getCourtListData(
            CourtListType listId,
            String courtCentreId,
            String courtRoomId,
            String startDate,
            String endDate,
            boolean restricted,
            String currentUserId) {
        String json = progressionQueryService.getCourtListPayload(
                listId, courtCentreId, courtRoomId, startDate, endDate, restricted, currentUserId);
        return json != null ? json : "{}";
    }

    /**
     * Fetches court list data from progression and returns as CourtListPayload.
     */
    public CourtListPayload getCourtListPayload(
            CourtListType listId,
            String courtCentreId,
            String startDate,
            String endDate,
            String cjscppuid) {
        boolean restricted = cjscppuid != null && !cjscppuid.trim().isEmpty();
        String json = getCourtListData(listId, courtCentreId, null, startDate, endDate, restricted, cjscppuid);
        try {
            return OBJECT_MAPPER.readValue(json, CourtListPayload.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize court list data to CourtListPayload: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse court list payload", e);
        }
    }
}
