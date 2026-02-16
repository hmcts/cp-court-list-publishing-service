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
import uk.gov.hmcts.cp.models.CourtCentreData;
import uk.gov.hmcts.cp.models.OuCourtroomsResponse;

import java.net.URI;
import java.util.Optional;

/**
 * Service to fetch court centre reference data using the ou-courtrooms API
 * (application/vnd.referencedata.ou-courtrooms+json) with courtId filter.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReferenceDataService {

    private final RestTemplate restTemplate;

    @Value("${common-platform-query-api.base-url:}")
    private String commonPlatformQueryApiBaseUrl;

    private static final String COURTROOMS_PATH = "/referencedata-query-api/query/api/rest/referencedata/courtrooms";
    private static final String ACCEPT_OU_COURTROOMS = "application/vnd.referencedata.ou-courtrooms+json";

    /**
     * Fetches court centre data by courtId (numeric string, e.g. "4" or "325").
     * Uses GET /courtrooms?courtId=... with Accept application/vnd.referencedata.ou-courtrooms+json.
     *
     * @param courtId   numeric court id from reference data (e.g. from listing or request)
     * @param cjscppuid user ID for CJSCPPUID header; set when non-blank.
     * @return optional with court centre data (id, ouCode, courtIdNumeric, etc.) if found
     */
    public Optional<CourtCentreData> getCourtCenterDataByCourtId(String courtId, String cjscppuid) {
        if (courtId == null || courtId.isBlank()) {
            log.debug("Court id is null or blank, skipping reference data lookup");
            return Optional.empty();
        }
        if (commonPlatformQueryApiBaseUrl == null || commonPlatformQueryApiBaseUrl.isBlank()) {
            log.warn("Reference data API base URL is not configured, skipping court centre lookup");
            return Optional.empty();
        }

        URI uri = UriComponentsBuilder
                .fromUriString(commonPlatformQueryApiBaseUrl)
                .path(COURTROOMS_PATH)
                .queryParam("courtId", courtId)
                .build()
                .toUri();

        return getCourtCentreData(uri, cjscppuid, "courtId", courtId);
    }

    /**
     * Fetches court centre data by courtCentreId (UUID).
     * Uses GET /courtrooms?courtCentreId=... with Accept application/vnd.referencedata.ou-courtrooms+json.
     *
     * @param courtCentreId court centre UUID (e.g. from publish request)
     * @param cjscppuid     user ID for CJSCPPUID header; set when non-blank.
     * @return optional with court centre data if found
     */
    public Optional<CourtCentreData> getCourtCenterDataByCourtCentreId(String courtCentreId, String cjscppuid) {
        if (courtCentreId == null || courtCentreId.isBlank()) {
            log.debug("Court centre id is null or blank, skipping reference data lookup");
            return Optional.empty();
        }
        if (commonPlatformQueryApiBaseUrl == null || commonPlatformQueryApiBaseUrl.isBlank()) {
            log.warn("Reference data API base URL is not configured, skipping court centre lookup");
            return Optional.empty();
        }

        URI uri = UriComponentsBuilder
                .fromUriString(commonPlatformQueryApiBaseUrl)
                .path(COURTROOMS_PATH)
                .queryParam("courtCentreId", courtCentreId)
                .build()
                .toUri();

        return getCourtCentreData(uri, cjscppuid, "courtCentreId", courtCentreId);
    }

    private Optional<CourtCentreData> getCourtCentreData(URI uri, String cjscppuid, String paramName, String paramValue) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", ACCEPT_OU_COURTROOMS);
        if (cjscppuid != null && !cjscppuid.isBlank()) {
            headers.set("CJSCPPUID", cjscppuid);
        }
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<OuCourtroomsResponse> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    requestEntity,
                    OuCourtroomsResponse.class
            );
            OuCourtroomsResponse body = response.getBody();
            if (body != null && body.getOrganisationunits() != null && !body.getOrganisationunits().isEmpty()) {
                CourtCentreData first = body.getOrganisationunits().get(0);
                log.info("Retrieved court centre data for {}: {}", paramName, paramValue);
                return Optional.of(first);
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to fetch court centre data by {} '{}': {}", paramName, paramValue, e.getMessage());
            return Optional.empty();
        }
    }
}
