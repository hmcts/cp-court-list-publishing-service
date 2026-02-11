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

import java.net.URI;
import java.util.Optional;

/**
 * Service to fetch court centre reference data (ouCode, courtId) by court name,
 * aligned with progression context getCourtCenterDataByCourtName step.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReferenceDataService {

    private final RestTemplate restTemplate;

    @Value("${common-platform-query-api.base-url:}")
    private String commonPlatformQueryApiBaseUrl;

    private static final String COURTROOMS_PATH = "/referencedata-query-api/query/api/rest/referencedata/courtrooms";
    private static final String ACCEPT_OU_COURTROOM_NAME = "application/vnd.referencedata.ou.courtrooms.ou-courtroom-name+json";

    /**
     * Fetches court centre data (id, ouCode, courtId) by court room name.
     * Maps to referencedata.query.ou.courtrooms.ou-courtroom-name.
     *
     * @param courtCentreName court name (e.g. from progression court list payload courtCentreName)
     * @param cjscppuid user ID for CJSCPPUID header; set when non-blank.
     * @return optional with id, ouCode and courtIdNumeric if found
     */
    public Optional<CourtCentreData> getCourtCenterDataByCourtName(String courtCentreName, String cjscppuid) {
        if (courtCentreName == null || courtCentreName.isBlank()) {
            log.debug("Court centre name is null or blank, skipping reference data lookup");
            return Optional.empty();
        }
        if (commonPlatformQueryApiBaseUrl == null || commonPlatformQueryApiBaseUrl.isBlank()) {
            log.warn("Reference data API base URL is not configured, skipping court centre lookup");
            return Optional.empty();
        }

        URI uri = UriComponentsBuilder
                .fromUriString(commonPlatformQueryApiBaseUrl)
                .path(COURTROOMS_PATH)
                .queryParam("ouCourtRoomName", courtCentreName)
                .build()
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", ACCEPT_OU_COURTROOM_NAME);
        if (cjscppuid != null && !cjscppuid.isBlank()) {
            headers.set("CJSCPPUID", cjscppuid);
        }
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<CourtCentreData> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    requestEntity,
                    CourtCentreData.class
            );
            CourtCentreData body = response.getBody();
            if (body != null) {
                log.info("Retrieved court centre data for court name: {}", courtCentreName);
                return Optional.of(body);
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to fetch court centre data by name '{}': {}", courtCentreName, e.getMessage());
            return Optional.empty();
        }
    }
}
