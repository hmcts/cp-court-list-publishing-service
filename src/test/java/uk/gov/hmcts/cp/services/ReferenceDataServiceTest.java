package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.cp.models.CourtCentreData;
import uk.gov.hmcts.cp.models.OuCourtroomsResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceDataServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private ReferenceDataService referenceDataService;

    private static final String BASE_URL = "http://reference-data:8080";

    @BeforeEach
    void setUp() {
        referenceDataService = new ReferenceDataService(restTemplate);
        ReflectionTestUtils.setField(referenceDataService, "commonPlatformQueryApiBaseUrl", BASE_URL);
    }

    @Test
    void getCourtCenterDataByCourtId_returnsData_whenApiReturnsSuccess() {
        String courtId = "325";
        CourtCentreData expected = CourtCentreData.builder()
                .id(UUID.fromString("f8254db1-1683-483e-afb3-b87fde5a0a26"))
                .ouCode("B01LY00")
                .courtIdNumeric("325")
                .build();
        OuCourtroomsResponse responseBody = new OuCourtroomsResponse(List.of(expected));

        when(restTemplate.exchange(
                any(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(OuCourtroomsResponse.class)
        )).thenReturn(ResponseEntity.ok(responseBody));

        Optional<CourtCentreData> result = referenceDataService.getCourtCenterDataByCourtId(courtId, "system-user");

        assertThat(result).isPresent();
        assertThat(result.get().getOuCode()).isEqualTo("B01LY00");
        assertThat(result.get().getCourtIdNumeric()).isEqualTo("325");
        assertThat(result.get().getId()).isEqualTo(UUID.fromString("f8254db1-1683-483e-afb3-b87fde5a0a26"));
        verify(restTemplate).exchange(any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(OuCourtroomsResponse.class));
    }

    @Test
    void getCourtCenterDataByCourtCentreId_returnsData_whenApiReturnsSuccess() {
        String courtCentreId = "f8254db1-1683-483e-afb3-b87fde5a0a26";
        CourtCentreData expected = CourtCentreData.builder()
                .id(UUID.fromString(courtCentreId))
                .ouCode("B01LY00")
                .courtIdNumeric("325")
                .build();
        OuCourtroomsResponse responseBody = new OuCourtroomsResponse(List.of(expected));

        when(restTemplate.exchange(
                any(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(OuCourtroomsResponse.class)
        )).thenReturn(ResponseEntity.ok(responseBody));

        Optional<CourtCentreData> result = referenceDataService.getCourtCenterDataByCourtCentreId(courtCentreId, "system-user");

        assertThat(result).isPresent();
        assertThat(result.get().getOuCode()).isEqualTo("B01LY00");
        assertThat(result.get().getCourtIdNumeric()).isEqualTo("325");
        verify(restTemplate).exchange(any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(OuCourtroomsResponse.class));
    }

    @Test
    void getCourtCenterDataByCourtId_returnsEmpty_whenCourtIdIsNull() {
        Optional<CourtCentreData> result = referenceDataService.getCourtCenterDataByCourtId(null, null);
        assertThat(result).isEmpty();
    }

    @Test
    void getCourtCenterDataByCourtId_returnsEmpty_whenCourtIdIsBlank() {
        Optional<CourtCentreData> result = referenceDataService.getCourtCenterDataByCourtId("   ", null);
        assertThat(result).isEmpty();
    }

    @Test
    void getCourtCenterDataByCourtId_returnsEmpty_whenBaseUrlNotConfigured() {
        ReflectionTestUtils.setField(referenceDataService, "commonPlatformQueryApiBaseUrl", "");

        Optional<CourtCentreData> result = referenceDataService.getCourtCenterDataByCourtId("325", null);

        assertThat(result).isEmpty();
    }

    @Test
    void getCourtCenterDataByCourtId_returnsEmpty_whenApiThrows() {
        when(restTemplate.exchange(any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(OuCourtroomsResponse.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        Optional<CourtCentreData> result = referenceDataService.getCourtCenterDataByCourtId("325", "system-user");

        assertThat(result).isEmpty();
    }

    @Test
    void getCourtCenterDataByCourtId_returnsEmpty_whenOrganisationunitsEmpty() {
        when(restTemplate.exchange(any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(OuCourtroomsResponse.class)))
                .thenReturn(ResponseEntity.ok(new OuCourtroomsResponse(List.of())));

        Optional<CourtCentreData> result = referenceDataService.getCourtCenterDataByCourtId("325", "system-user");

        assertThat(result).isEmpty();
    }
}
