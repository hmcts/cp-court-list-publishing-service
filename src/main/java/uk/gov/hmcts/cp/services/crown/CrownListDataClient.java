package uk.gov.hmcts.cp.services.crown;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.cp.config.AppConstant;

import java.util.List;

/**
 * Fetches a crown court list from listing with the extra data the CaTH PDDA lists need:
 * {@code GET /listing-service/query/api/rest/listing/dailylistpayload}, Accept
 * {@code application/vnd.listing.search.crown.list.payload+json}. A week commencing list (FIRM, WARN) is asked
 * for with {@code weekCommencingStartDate}/{@code weekCommencingEndDate}, a daily list with
 * {@code startDate}/{@code endDate}, as listing's API requires.
 */
@Component
@Slf4j
public class CrownListDataClient {

    static final String PATH = "/listing-service/query/api/rest/listing/dailylistpayload";
    static final MediaType CROWN_LIST_PAYLOAD =
            MediaType.parseMediaType("application/vnd.listing.search.crown.list.payload+json");

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public CrownListDataClient(RestTemplate restTemplate,
                               @Value("${common-platform-query-api.base-url:}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl == null ? "" : baseUrl;
    }

    /** The listing payload as JSON. */
    public String fetch(CrownPublishRequest request) {
        if (baseUrl.isBlank()) {
            throw new IllegalStateException("common-platform-query-api.base-url is not configured");
        }
        boolean weekCommencing = request.listType().weekCommencing();
        String url = UriComponentsBuilder.fromUriString(baseUrl).path(PATH)
                .queryParam("publishCourtListType", request.listType().courtListType().name())
                .queryParam("courtCentreId", request.courtCentreId())
                .queryParam(weekCommencing ? "weekCommencingStartDate" : "startDate", request.startDate())
                .queryParam(weekCommencing ? "weekCommencingEndDate" : "endDate", request.endDate())
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(CROWN_LIST_PAYLOAD));
        headers.set(AppConstant.CJSCPPUID, request.userId());

        try {
            String body = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
            if (body == null || body.isBlank()) {
                throw new IllegalStateException("Listing returned an empty crown list payload");
            }
            return body;
        } catch (RestClientException e) {
            log.error("Crown list payload call failed, {}, courtCentreId={}", request.describe(), request.courtCentreId(), e);
            throw new IllegalStateException("Failed to fetch crown list payload from listing: " + e.getMessage(), e);
        }
    }
}
