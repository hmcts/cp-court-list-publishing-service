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
import uk.gov.hmcts.cp.models.CourtListPayload;

import java.net.URI;

@Service
@RequiredArgsConstructor
@Slf4j
public class ListingQueryService {

    private final RestTemplate restTemplate;

    @Value("${common-platform-query-api.base-url}")
    private String commonPlatformQueryApiBaseUrl;

    public CourtListPayload getCourtListPayload(String listId, String courtCentreId, String startDate, String endDate, String cjscppuid) {
        log.info("Fetching court list payload for listId: {}, courtCentreId: {}, startDate: {}, endDate: {}",
                listId, courtCentreId, startDate, endDate);

        URI uri = UriComponentsBuilder
                .fromUriString(commonPlatformQueryApiBaseUrl)
                .path("/listing-query-api/query/api/rest/listing/courtlistpayload")
                .queryParam("listId", listId)
                .queryParam("courtCentreId", courtCentreId)
                .queryParam("startDate", startDate)
                .queryParam("endDate", endDate)
                .build()
                .toUri();

        log.debug("Calling listing-query-api with URI: {}", uri);

        // Set required headers
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.listing.search.court.list.payload+json");
        if (cjscppuid != null && !cjscppuid.trim().isEmpty()) {
            headers.set("CJSCPPUID", cjscppuid);
        }
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<CourtListPayload> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    requestEntity,
                    CourtListPayload.class
            );
            log.info("Successfully retrieved court list payload");
            return response.getBody();
        } catch (Exception e) {
            log.error("Error calling common-platform-query-api: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch court list payload from common-platform-query-api", e);
        }
    }
}

