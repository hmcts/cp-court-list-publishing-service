package uk.gov.hmcts.cp.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.cp.config.AppConstant;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;
import uk.gov.hmcts.cp.models.CourtListPayload;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.services.courtlistdownload.CourtListDownloadException;
import uk.gov.hmcts.cp.services.courtlistdownload.CourtListFileResult;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

@Service
@Slf4j
public class CourtListDataService {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperConfig.getObjectMapper();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String COURT_LIST_PATH = "/listing-service/query/api/rest/listing/courtlist";
    private static final String ACCEPT_COURTLIST = "application/vnd.listing.search.court.list+json";
    private static final String CONTENT_TYPE_PDF = "application/pdf";
    private static final String CONTENT_TYPE_WORD =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String PDF_FILENAME = "CourtList.pdf";
    private static final String WORD_FILENAME = "CourtList.docx";

    private static final Set<CourtListType> DOWNLOAD_TYPES_COURTLIST_API = EnumSet.of(
            CourtListType.PUBLIC,
            CourtListType.BENCH,
            CourtListType.ALPHABETICAL,
            CourtListType.JUDGE,
            CourtListType.USHERS_CROWN,
            CourtListType.USHERS_MAGISTRATE);

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

    /**
     * Calls listing's /courtlist endpoint which returns the rendered document directly (PDF for
     * PUBLIC/BENCH/ALPHABETICAL/JUDGE, Word for USHERS_CROWN/USHERS_MAGISTRATE). Listing dispatches
     * the right assembler per type, so this works for every supported listId. The response Content-Type
     * tells us PDF vs Word; we pick the filename accordingly.
     */
    public CourtListFileResult getCourtListDocumentForDownload(
            CourtListType courtListType, String courtCentreId, String courtRoomId,
            LocalDate startDate, LocalDate endDate, String cjscppuid) {
        if (!DOWNLOAD_TYPES_COURTLIST_API.contains(courtListType)) {
            throw new CourtListDownloadException("Unsupported court list type for download: " + courtListType);
        }
        if (courtListDataBaseUrl.isBlank()) {
            throw new CourtListDownloadException("Court list data is not configured");
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(courtListDataBaseUrl).path(COURT_LIST_PATH)
                .queryParam("listId", courtListType.name())
                .queryParam("courtCentreId", courtCentreId)
                .queryParam("startDate", startDate.format(DATE_FORMAT))
                .queryParam("endDate", endDate.format(DATE_FORMAT))
                .queryParam("restricted", false);
        if (courtRoomId != null && !courtRoomId.isBlank()) {
            builder.queryParam("courtRoomId", courtRoomId);
        }
        String url = builder.build().toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.parseMediaType(ACCEPT_COURTLIST)));
        if (cjscppuid != null && !cjscppuid.isBlank()) {
            headers.set(AppConstant.CJSCPPUID, cjscppuid);
        }

        try {
            ResponseEntity<byte[]> response = publicCourtListRestTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
            byte[] body = response.getBody();
            if (body == null || body.length == 0) {
                throw new CourtListDownloadException("Court list API returned empty response");
            }
            MediaType contentType = response.getHeaders().getContentType();
            boolean isWord = contentType != null
                    && contentType.toString().toLowerCase().contains("wordprocessingml");
            return isWord
                    ? new CourtListFileResult(body, CONTENT_TYPE_WORD, WORD_FILENAME)
                    : new CourtListFileResult(body, CONTENT_TYPE_PDF, PDF_FILENAME);
        } catch (RestClientException e) {
            log.error("Court list API call failed for listId={}, courtCentreId={}", courtListType, courtCentreId, e);
            throw new CourtListDownloadException("Failed to fetch court list: " + e.getMessage(), e);
        }
    }
}
