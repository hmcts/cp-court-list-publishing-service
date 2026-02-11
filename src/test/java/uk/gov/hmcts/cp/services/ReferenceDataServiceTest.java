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
    void getCourtCenterDataByCourtName_returnsData_whenApiReturnsSuccess() {
        String courtName = "Lavender Hill Magistrates' Court";
        CourtCentreData expected = CourtCentreData.builder()
                .id(UUID.fromString("f8254db1-1683-483e-afb3-b87fde5a0a26"))
                .ouCode("B01LY00")
                .courtIdNumeric("325")
                .build();

        when(restTemplate.exchange(
                any(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(CourtCentreData.class)
        )).thenReturn(ResponseEntity.ok(expected));

        Optional<CourtCentreData> result = referenceDataService.getCourtCenterDataByCourtName(courtName, "system-user");

        assertThat(result).isPresent();
        assertThat(result.get().getOuCode()).isEqualTo("B01LY00");
        assertThat(result.get().getCourtIdNumeric()).isEqualTo("325");
        assertThat(result.get().getId()).isEqualTo(UUID.fromString("f8254db1-1683-483e-afb3-b87fde5a0a26"));
        verify(restTemplate).exchange(any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(CourtCentreData.class));
    }

    @Test
    void getCourtCenterDataByCourtName_returnsEmpty_whenCourtNameIsNull() {
        Optional<CourtCentreData> result = referenceDataService.getCourtCenterDataByCourtName(null, null);
        assertThat(result).isEmpty();
    }

    @Test
    void getCourtCenterDataByCourtName_returnsEmpty_whenCourtNameIsBlank() {
        Optional<CourtCentreData> result = referenceDataService.getCourtCenterDataByCourtName("   ", null);
        assertThat(result).isEmpty();
    }

    @Test
    void getCourtCenterDataByCourtName_returnsEmpty_whenBaseUrlNotConfigured() {
        ReflectionTestUtils.setField(referenceDataService, "commonPlatformQueryApiBaseUrl", "");

        Optional<CourtCentreData> result = referenceDataService.getCourtCenterDataByCourtName("Some Court", null);

        assertThat(result).isEmpty();
    }

    @Test
    void getCourtCenterDataByCourtName_returnsEmpty_whenApiThrows() {
        when(restTemplate.exchange(any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(CourtCentreData.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        Optional<CourtCentreData> result = referenceDataService.getCourtCenterDataByCourtName("Some Court", "system-user");

        assertThat(result).isEmpty();
    }
}
