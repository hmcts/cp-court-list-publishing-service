package uk.gov.hmcts.cp.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;
import uk.gov.hmcts.cp.models.CourtListPayload;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.services.courtlistdownload.CourtListDownloadException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;

@Service
@Slf4j
public class CourtListDataService {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperConfig.getObjectMapper();
    private static final String LIST_ID_PUBLIC = "PUBLIC";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String COURT_LIST_DATA_PATH = "courtlist";

    private final ProgressionQueryService progressionQueryService;
    private final RestTemplate publicCourtListRestTemplate;
    private final String courtListDataBaseUrl;

    public CourtListDataService(
            final ProgressionQueryService progressionQueryService,
            final RestTemplate publicCourtListRestTemplate,
            @Value("${common-platform-query-api.base-url:}") final String courtListDataBaseUrl) {
        this.progressionQueryService = progressionQueryService;
        this.publicCourtListRestTemplate = publicCourtListRestTemplate;
        this.courtListDataBaseUrl = courtListDataBaseUrl != null ? courtListDataBaseUrl : "";
    }

    public String getCourtListData(
            CourtListType listId,
            String courtCentreId,
            String courtRoomId,
            String startDate,
            String endDate,
            boolean restricted,
            String currentUserId,
            boolean includeApplications) {
        String json = progressionQueryService.getCourtListPayload(
                listId, courtCentreId, courtRoomId, startDate, endDate, restricted, currentUserId, includeApplications);
        return json != null ? json : "{}";
    }

    public CourtListPayload getCourtListPayload(
            CourtListType listId,
            String courtCentreId,
            String startDate,
            String endDate,
            String cjscppuid,
            boolean includeApplications) {
        boolean restricted = cjscppuid != null && !cjscppuid.trim().isEmpty();
        String json = getCourtListData(listId, courtCentreId, null, startDate, endDate, restricted, cjscppuid, includeApplications);

        try {
            return OBJECT_MAPPER.readValue(json, CourtListPayload.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize court list data to CourtListPayload: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to parse court list payload", e);
        }
    }

    public Map<String, Object> getPublicCourtListPayload(String courtCentreId, LocalDate startDate, LocalDate endDate) {
        if (courtListDataBaseUrl.isBlank()) {
            throw new CourtListDownloadException("Public court list data is not configured");
        }
        String url = UriComponentsBuilder.fromUriString(courtListDataBaseUrl + "/" + COURT_LIST_DATA_PATH)
                .queryParam("listId", LIST_ID_PUBLIC)
                .queryParam("courtCentreId", courtCentreId)
                .queryParam("startDate", startDate.format(DATE_FORMAT))
                .queryParam("endDate", endDate.format(DATE_FORMAT))
                .queryParam("restricted", false)
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(
                MediaType.parseMediaType("application/vnd.courtlist.search.court.list+json")));

        try {
            ResponseEntity<Map<String, Object>> response = publicCourtListRestTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            return response.getBody();
        } catch (RestClientException e) {
            log.error("Court list data API call failed for courtCentreId={}", courtCentreId, e);
            throw new CourtListDownloadException("Failed to fetch court list: " + e.getMessage(), e);
        }
    }
}
