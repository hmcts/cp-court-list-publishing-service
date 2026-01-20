package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.cp.models.transformed.CourtListDocument;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaTHServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private CaTHService cathService;

    private CourtListDocument courtListDocument;
    private String baseUrl;
    private String endpoint;

    @BeforeEach
    void setUp() {
        // Use reflection to set the base URL and endpoint for testing
        baseUrl = "https://spnl-apim-int-gw.cpp.nonlive";
        endpoint = "/courtlistpublisher/publication";
        try {
            java.lang.reflect.Field baseUrlField = CaTHService.class.getDeclaredField("cathBaseUrl");
            baseUrlField.setAccessible(true);
            baseUrlField.set(cathService, baseUrl);

            java.lang.reflect.Field endpointField = CaTHService.class.getDeclaredField("cathEndpoint");
            endpointField.setAccessible(true);
            endpointField.set(cathService, endpoint);
        } catch (Exception e) {
            // Ignore if reflection fails
        }

        courtListDocument = CourtListDocument.builder().build();
    }

    @Test
    void sendCourtListToCaTH_shouldCallApiWithCorrectParametersAndHeaders() {
        // Given
        ResponseEntity<String> responseEntity = new ResponseEntity<>("Success", HttpStatus.OK);

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<CourtListDocument>> httpEntityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        when(restTemplate.exchange(
                any(URI.class),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(responseEntity);

        // When
        cathService.sendCourtListToCaTH(courtListDocument);

        // Then - Verify RestTemplate was called
        verify(restTemplate).exchange(
                uriCaptor.capture(),
                eq(HttpMethod.POST),
                httpEntityCaptor.capture(),
                eq(String.class)
        );

        // Verify URI is correct
        URI capturedUri = uriCaptor.getValue();
        assertThat(capturedUri.toString()).isEqualTo(baseUrl + endpoint);

        // Verify headers are set correctly
        HttpEntity<CourtListDocument> capturedEntity = httpEntityCaptor.getValue();
        assertThat(capturedEntity).isNotNull();
        assertThat(capturedEntity.getBody()).isEqualTo(courtListDocument);
        
        HttpHeaders headers = capturedEntity.getHeaders();
        assertThat(headers).isNotNull();
        assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void sendCourtListToCaTH_shouldThrowException_whenRestClientExceptionOccurs() {
        // Given
        when(restTemplate.exchange(
                any(URI.class),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenThrow(new RestClientException("Connection failed"));

        // When & Then
        assertThatThrownBy(() -> cathService.sendCourtListToCaTH(courtListDocument))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to send court list document to CaTH");
    }

    @Test
    void sendCourtListToCaTH_shouldThrowException_whenGenericExceptionOccurs() {
        // Given
        when(restTemplate.exchange(
                any(URI.class),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenThrow(new RuntimeException("Unexpected error"));

        // When & Then
        assertThatThrownBy(() -> cathService.sendCourtListToCaTH(courtListDocument))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to send court list document to CaTH");
    }
}
