package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.cp.openapi.model.CourtListType;

import java.net.URI;

/**
 * Calls progression service GET /courtlistdata (application/vnd.progression.search.court.list.data+json).
 * Returns the same court list data shape as progression's court list document payload, without PDF/Word generation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProgressionQueryService {

    private static final String PROGRESSION_COURTLISTDATA_PATH = "/progression-service/query/api/rest/progression/courtlistdata";
    private static final String ACCEPT_COURT_LIST_DATA = "application/vnd.progression.search.court.list.data+json";
    private static final String ACCEPT_PRISON_COURT_LIST_DATA = "application/vnd.progression.search.prison.court.list.data+json";
    private static final String PRISON = "PRISON";
    /** Header name for user ID (progression query API expects this). */
    private static final String USER_ID_HEADER = "userId";

    private final RestTemplate restTemplate;

    @Value("${common-platform-query-api.base-url:}")
    private String baseUrl;

    /**
     * Fetches court list data from progression /courtlistdata.
     * Returns raw JSON string (same shape as progression court list payload, optionally enriched with ouCode/courtId).
     *
     * @param cjscppuid user ID for the request; set on User-Id header when non-blank.
     */
    public String getCourtListPayload(
            CourtListType listId,
            String courtCentreId,
            String courtRoomId,
            String startDate,
            String endDate,
            boolean restricted,
            String cjscppuid) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("common-platform-query-api.base-url is not configured");
        }
        log.info("Fetching court list data from progression for listId: {}, courtCentreId: {}, startDate: {}, endDate: {}",
                listId, courtCentreId, startDate, endDate);

        var builder = UriComponentsBuilder
                .fromUriString(baseUrl)
                .path(PROGRESSION_COURTLISTDATA_PATH)
                .queryParam("courtCentreId", courtCentreId)
                .queryParam("startDate", startDate)
                .queryParam("endDate", endDate)
                .queryParam("restricted", restricted);
        if (PRISON.equals(listId.name())) {
            // Prison list: no listId query param, use prison Accept
        } else {
            builder.queryParam("listId", listId.name());
        }
        if (courtRoomId != null && !courtRoomId.isBlank()) {
            builder.queryParam("courtRoomId", courtRoomId);
        }
        URI uri = builder.build().toUri();

        String accept = PRISON.equals(listId.name()) ? ACCEPT_PRISON_COURT_LIST_DATA : ACCEPT_COURT_LIST_DATA;
        log.debug("Calling progression courtlistdata with URI: {}, Accept: {}", uri, accept);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", accept);
        if (cjscppuid != null && !cjscppuid.isBlank()) {
            headers.set(USER_ID_HEADER, cjscppuid);
        }
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                requestEntity,
                String.class
        );
        log.info("Successfully retrieved court list data from progression");
        return response.getBody() != null ? response.getBody() : "{}";
    }
}
