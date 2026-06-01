package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.cp.models.CourtListPayload;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.services.courtlistdownload.CourtListDownloadException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourtListDataServiceTest {

    private static final String LISTING_BASE_URL = "https://internal.example.com";
    private static final String LISTING_PATH = "/listing-service/query/api/rest/listing/courtlistpayload";

    @Mock
    private RestTemplate publicCourtListRestTemplate;

    @Mock
    private ProgressionQueryService progressionQueryService;

    private CourtListDataService courtListDataService;

    @BeforeEach
    void setUp() {
        courtListDataService = new CourtListDataService(progressionQueryService, publicCourtListRestTemplate, LISTING_BASE_URL);
    }

    @Test
    void getCourtListDataRoutesStandardThroughProgressionForRefdataEnrichment() {
        String enrichedJson = "{\"listType\":\"standard\",\"courtCentreName\":\"Lavender Hill\",\"ouCode\":\"B01LY00\",\"courtId\":\"f8254db1-1683-483e-afb3-b87fde5a0a26\",\"courtIdNumeric\":\"42\",\"isWelsh\":false}";
        when(progressionQueryService.getCourtListPayload(
                eq(CourtListType.STANDARD), anyString(), any(), anyString(), anyString(), anyBoolean(), anyString(), anyBoolean()))
                .thenReturn(enrichedJson);

        String result = courtListDataService.getCourtListData(
                CourtListType.STANDARD, "f8254db1-1683-483e-afb3-b87fde5a0a26", null,
                "2024-01-15", "2024-01-15", false, "request-user-id", false);

        assertThat(result).isEqualTo(enrichedJson);
        verify(progressionQueryService).getCourtListPayload(
                CourtListType.STANDARD, "f8254db1-1683-483e-afb3-b87fde5a0a26", null,
                "2024-01-15", "2024-01-15", false, "request-user-id", false);
        verifyNoInteractions(publicCourtListRestTemplate);
    }

    @Test
    void getCourtListDataRoutesPublicThroughProgression() {
        when(progressionQueryService.getCourtListPayload(
                eq(CourtListType.PUBLIC), anyString(), any(), anyString(), anyString(), anyBoolean(), anyString(), anyBoolean()))
                .thenReturn("{\"listType\":\"public\"}");

        courtListDataService.getCourtListData(
                CourtListType.PUBLIC, "courtCentre", null, "2026-01-05", "2026-01-12", false, "user", false);

        verify(progressionQueryService).getCourtListPayload(
                eq(CourtListType.PUBLIC), eq("courtCentre"), any(), eq("2026-01-05"), eq("2026-01-12"), eq(false), eq("user"), eq(false));
        verifyNoInteractions(publicCourtListRestTemplate);
    }

    @Test
    void getCourtListDataRoutesBenchThroughProgression() {
        when(progressionQueryService.getCourtListPayload(
                eq(CourtListType.BENCH), anyString(), any(), anyString(), anyString(), anyBoolean(), anyString(), anyBoolean()))
                .thenReturn("{\"listType\":\"bench\"}");

        courtListDataService.getCourtListData(
                CourtListType.BENCH, "courtCentre", null, "2026-01-05", "2026-01-12", false, "user", false);

        verify(progressionQueryService).getCourtListPayload(
                eq(CourtListType.BENCH), eq("courtCentre"), any(), eq("2026-01-05"), eq("2026-01-12"), eq(false), eq("user"), eq(false));
        verifyNoInteractions(publicCourtListRestTemplate);
    }

    @Test
    void getCourtListDataCallsListingDirectlyForNonEnrichedTypes() {
        String listingJson = "{\"listType\":\"alphabetical\",\"templateName\":\"CourtList\"}";
        when(publicCourtListRestTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(listingJson, HttpStatus.OK));

        String result = courtListDataService.getCourtListData(
                CourtListType.ALPHABETICAL, "courtCentre", null, "2026-01-05", "2026-01-12", false, "user", false);

        assertThat(result).isEqualTo(listingJson);
        verify(publicCourtListRestTemplate).exchange(
                argThat((String url) -> url.contains(LISTING_PATH) && url.contains("listId=ALPHABETICAL")),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        verify(progressionQueryService, never()).getCourtListPayload(
                any(), any(), any(), any(), any(), anyBoolean(), any(), anyBoolean());
    }

    @Test
    void getCourtListDataRoutesUshersCrownThroughProgression() {
        when(progressionQueryService.getCourtListPayload(
                eq(CourtListType.USHERS_CROWN), anyString(), any(), anyString(), anyString(), anyBoolean(), anyString(), anyBoolean()))
                .thenReturn("{\"listType\":\"ushers_crown\"}");

        courtListDataService.getCourtListData(
                CourtListType.USHERS_CROWN, "courtCentre", null, "2026-01-05", "2026-01-12", false, "user", false);

        verify(progressionQueryService).getCourtListPayload(
                eq(CourtListType.USHERS_CROWN), eq("courtCentre"), any(), eq("2026-01-05"), eq("2026-01-12"), eq(false), eq("user"), eq(false));
        verifyNoInteractions(publicCourtListRestTemplate);
    }

    @Test
    void getCourtListDataRoutesUshersMagistrateThroughProgression() {
        when(progressionQueryService.getCourtListPayload(
                eq(CourtListType.USHERS_MAGISTRATE), anyString(), any(), anyString(), anyString(), anyBoolean(), anyString(), anyBoolean()))
                .thenReturn("{\"listType\":\"ushers_magistrate\"}");

        courtListDataService.getCourtListData(
                CourtListType.USHERS_MAGISTRATE, "courtCentre", null, "2026-01-05", "2026-01-12", false, "user", false);

        verify(progressionQueryService).getCourtListPayload(
                eq(CourtListType.USHERS_MAGISTRATE), eq("courtCentre"), any(), eq("2026-01-05"), eq("2026-01-12"), eq(false), eq("user"), eq(false));
        verifyNoInteractions(publicCourtListRestTemplate);
    }

    @Test
    void getCourtListData_throwsWhenListingReturnsEmptyBody() {
        when(publicCourtListRestTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>((String) null, HttpStatus.OK));

        assertThatThrownBy(() -> courtListDataService.getCourtListData(
                CourtListType.ALPHABETICAL, "f8254db1-1683-483e-afb3-b87fde5a0a26", null,
                "2024-01-15", "2024-01-15", false, "user", false))
                .isInstanceOf(CourtListDownloadException.class)
                .hasMessageContaining("empty response");
    }

    @Test
    void getCourtListPayloadCallsProgressionWithRestrictedTrueWhenCjscppuidPresent() {
        String json = "{\"listType\":\"standard\",\"courtCentreName\":\"Test Court\",\"ouCode\":\"B01LY\",\"courtId\":\"f8254db1-1683-483e-afb3-b87fde5a0a26\"}";
        when(progressionQueryService.getCourtListPayload(
                eq(CourtListType.STANDARD), anyString(), any(), anyString(), anyString(), eq(true), anyString(), anyBoolean()))
                .thenReturn(json);

        CourtListPayload result = courtListDataService.getCourtListPayload(
                CourtListType.STANDARD, "courtCentre1", "2026-01-05", "2026-01-12", "user-id", false);

        assertThat(result).isNotNull();
        assertThat(result.getListType()).isEqualTo("standard");
        assertThat(result.getCourtCentreName()).isEqualTo("Test Court");
        assertThat(result.getOuCode()).isEqualTo("B01LY");
        assertThat(result.getCourtId()).isEqualTo("f8254db1-1683-483e-afb3-b87fde5a0a26");
        verify(progressionQueryService).getCourtListPayload(
                eq(CourtListType.STANDARD), eq("courtCentre1"), any(), eq("2026-01-05"), eq("2026-01-12"), eq(true), eq("user-id"), eq(false));
    }

    @Test
    void getCourtListPayload_throws_whenProgressionReturnsInvalidJson() {
        when(progressionQueryService.getCourtListPayload(
                eq(CourtListType.STANDARD), anyString(), any(), anyString(), anyString(), anyBoolean(), any(), anyBoolean()))
                .thenReturn("not valid json {{{");

        assertThatThrownBy(() -> courtListDataService.getCourtListPayload(
                CourtListType.STANDARD, "courtCentre1", "2026-01-05", "2026-01-12", null, false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to parse court list payload");
    }

    @Test
    void getCourtListPayloadForDownloadRoutesStandardThroughProgressionWithRestrictedTrue() {
        String json = "{\"listType\":\"standard\",\"templateName\":\"BenchAndStandardCourtList\",\"ouCode\":\"B01LY\"}";
        when(progressionQueryService.getCourtListPayload(
                eq(CourtListType.STANDARD), anyString(), any(), anyString(), anyString(), eq(true), anyString(), eq(false)))
                .thenReturn(json);

        String result = courtListDataService.getCourtListPayloadForDownload(
                CourtListType.STANDARD, "f8254db1-1683-483e-afb3-b87fde5a0a26", null,
                LocalDate.of(2026, 2, 27), LocalDate.of(2026, 2, 27), "user-id", true);

        assertThat(result).isEqualTo(json);
        verify(progressionQueryService).getCourtListPayload(
                eq(CourtListType.STANDARD), eq("f8254db1-1683-483e-afb3-b87fde5a0a26"), any(),
                eq("2026-02-27"), eq("2026-02-27"), eq(true), eq("user-id"), eq(false));
        verifyNoInteractions(publicCourtListRestTemplate);
    }

    @Test
    void getCourtListPayloadForDownloadRoutesStandardThroughProgressionWithRestrictedFalse() {
        String json = "{\"listType\":\"standard\"}";
        when(progressionQueryService.getCourtListPayload(
                eq(CourtListType.STANDARD), anyString(), any(), anyString(), anyString(), eq(false), anyString(), eq(false)))
                .thenReturn(json);

        String result = courtListDataService.getCourtListPayloadForDownload(
                CourtListType.STANDARD, "f8254db1-1683-483e-afb3-b87fde5a0a26", null,
                LocalDate.of(2026, 2, 27), LocalDate.of(2026, 2, 27), "user-id", false);

        assertThat(result).isEqualTo(json);
        verify(progressionQueryService).getCourtListPayload(
                eq(CourtListType.STANDARD), eq("f8254db1-1683-483e-afb3-b87fde5a0a26"), any(),
                eq("2026-02-27"), eq("2026-02-27"), eq(false), eq("user-id"), eq(false));
    }

    @Test
    void getCourtListPayloadForDownloadRoutesUshersCrownThroughProgression() {
        String json = "{\"listType\":\"ushers_crown\",\"templateName\":\"UshersCrownList\"}";
        when(progressionQueryService.getCourtListPayload(
                eq(CourtListType.USHERS_CROWN), anyString(), any(), anyString(), anyString(), eq(false), anyString(), eq(false)))
                .thenReturn(json);

        String result = courtListDataService.getCourtListPayloadForDownload(
                CourtListType.USHERS_CROWN, "f8254db1-1683-483e-afb3-b87fde5a0a26", null,
                LocalDate.of(2026, 2, 27), LocalDate.of(2026, 2, 27), "user-id", false);

        assertThat(result).isEqualTo(json);
        verify(progressionQueryService).getCourtListPayload(
                eq(CourtListType.USHERS_CROWN), eq("f8254db1-1683-483e-afb3-b87fde5a0a26"), any(),
                eq("2026-02-27"), eq("2026-02-27"), eq(false), eq("user-id"), eq(false));
        verifyNoInteractions(publicCourtListRestTemplate);
    }

    @Test
    void getCourtListPayloadForDownloadRoutesUshersMagistrateThroughProgression() {
        String json = "{\"listType\":\"ushers_magistrate\",\"templateName\":\"UshersMagistrateList\"}";
        when(progressionQueryService.getCourtListPayload(
                eq(CourtListType.USHERS_MAGISTRATE), anyString(), any(), anyString(), anyString(), eq(false), anyString(), eq(false)))
                .thenReturn(json);

        String result = courtListDataService.getCourtListPayloadForDownload(
                CourtListType.USHERS_MAGISTRATE, "f8254db1-1683-483e-afb3-b87fde5a0a26", null,
                LocalDate.of(2026, 2, 27), LocalDate.of(2026, 2, 27), "user-id", false);

        assertThat(result).isEqualTo(json);
        verify(progressionQueryService).getCourtListPayload(
                eq(CourtListType.USHERS_MAGISTRATE), eq("f8254db1-1683-483e-afb3-b87fde5a0a26"), any(),
                eq("2026-02-27"), eq("2026-02-27"), eq(false), eq("user-id"), eq(false));
        verifyNoInteractions(publicCourtListRestTemplate);
    }

    @Test
    void getCourtListPayloadForDownload_throwsWhenBaseUrlNotConfiguredForListingPath() {
        CourtListDataService serviceWithNoUrl = new CourtListDataService(
                progressionQueryService, publicCourtListRestTemplate, "");

        assertThatThrownBy(() -> serviceWithNoUrl.getCourtListPayloadForDownload(
                CourtListType.ALPHABETICAL, "f8254db1-1683-483e-afb3-b87fde5a0a26", null,
                LocalDate.of(2026, 2, 27), LocalDate.of(2026, 2, 27), "user-id", false))
                .isInstanceOf(CourtListDownloadException.class)
                .hasMessageContaining("Court list data is not configured");
    }
}
