package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.cp.openapi.model.CourtListType;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingQueryServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private ListingQueryService listingQueryService;

    @BeforeEach
    void setUp() throws Exception {
        var field = ListingQueryService.class.getDeclaredField("baseUrl");
        field.setAccessible(true);
        field.set(listingQueryService, "https://listing.example.com");
    }

    @Test
    void getCourtListPayload_callsListingCourtlistpayloadWithCorrectParams() {
        String expectedJson = "{\"listType\":\"standard\",\"courtCentreName\":\"Test\"}";
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(expectedJson, HttpStatus.OK));

        String result = listingQueryService.getCourtListPayload(
                CourtListType.STANDARD,
                "f8254db1-1683-483e-afb3-b87fde5a0a26",
                null,
                "2024-01-15",
                "2024-01-15",
                false,
                "test-cjscppuid");

        assertThat(result).isEqualTo(expectedJson);
        verify(restTemplate).exchange(any(URI.class), eq(HttpMethod.GET), any(), eq(String.class));
    }

    @Test
    void getCourtListPayload_throwsWhenBaseUrlNotConfigured() throws Exception {
        var field = ListingQueryService.class.getDeclaredField("baseUrl");
        field.setAccessible(true);
        field.set(listingQueryService, "");

        assertThatThrownBy(() -> listingQueryService.getCourtListPayload(
                CourtListType.STANDARD,
                "f8254db1-1683-483e-afb3-b87fde5a0a26",
                null,
                "2024-01-15",
                "2024-01-15",
                false,
                null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base-url is not configured");
    }
}
