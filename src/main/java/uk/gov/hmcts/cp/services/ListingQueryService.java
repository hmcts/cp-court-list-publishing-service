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
 * Calls listing service court list payload endpoint only (no progression).
 * Same contract as progression's /courtlistdata source: listing.search.court.list.payload.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ListingQueryService {

    private static final String LISTING_COURTLIST_PAYLOAD_PATH = "/listing-service/query/api/rest/listing/courtlistpayload";
    private static final String ACCEPT_LISTING_PAYLOAD = "application/vnd.listing.search.court.list.payload+json";

    private final RestTemplate restTemplate;

    @Value("${common-platform-query-api.base-url:}")
    private String baseUrl;

    /**
     * Fetches court list payload from listing only (no progression).
     * Returns raw JSON string to preserve exact response shape.
     */
    public String getCourtListPayload(
            CourtListType listId,
            String courtCentreId,
            String courtRoomId,
            String startDate,
            String endDate,
            boolean restricted) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("common-platform-query-api.base-url is not configured");
        }
        log.info("Fetching court list payload from listing for listId: {}, courtCentreId: {}, startDate: {}, endDate: {}",
                listId, courtCentreId, startDate, endDate);

        var builder = UriComponentsBuilder
                .fromUriString(baseUrl)
                .path(LISTING_COURTLIST_PAYLOAD_PATH)
                .queryParam("listId", listId.name())
                .queryParam("courtCentreId", courtCentreId)
                .queryParam("startDate", startDate)
                .queryParam("endDate", endDate)
                .queryParam("restricted", restricted);
        if (courtRoomId != null && !courtRoomId.isBlank()) {
            builder.queryParam("courtRoomId", courtRoomId);
        }
        URI uri = builder.build().toUri();

        log.debug("Calling listing courtlistpayload with URI: {}", uri);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", ACCEPT_LISTING_PAYLOAD);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                requestEntity,
                String.class
        );
        log.info("Successfully retrieved court list payload from listing");
        return response.getBody() != null ? response.getBody() : "{}";
    }
}
