package uk.gov.hmcts.cp.services.sjp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.cp.api.sjp.PublishSjpCourtListRequest;
import uk.gov.hmcts.cp.api.sjp.PublishSjpCourtListResponse;
import uk.gov.hmcts.cp.api.sjp.SjpListPayload;
import uk.gov.hmcts.cp.services.PublishingService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SjpCourtListPublishServiceTest {

    @Mock
    private SjpToCathPayloadTransformer transformer;

    @Mock
    private PublishingService publishingService;

    private SjpCourtListPublishService service;

    @BeforeEach
    void setUp() {
        service = new SjpCourtListPublishService(transformer, publishingService);
        ReflectionTestUtils.setField(service, "cathPublishingEnabled", true);
        ReflectionTestUtils.setField(service, "azureLocalDtsApimUrl", "https://apim.example.com/publish");
    }

    @Test
    void publishSjpCourtList_returnsFailed_whenListPayloadMissing() {
        PublishSjpCourtListRequest request = new PublishSjpCourtListRequest(
                PublishSjpCourtListRequest.SJP_PUBLISH_LIST, "ENGLISH", "FULL", null);

        PublishSjpCourtListResponse response = service.publishSjpCourtList(request);

        assertThat(response.getStatus()).isEqualTo("FAILED");
        assertThat(response.getMessage()).contains("listPayload is required");
    }

    @Test
    void publishSjpCourtList_returnsAccepted_whenReadyCasesEmpty() {
        SjpListPayload listPayload = new SjpListPayload("2024-01-01 00:00:00", List.of());
        PublishSjpCourtListRequest request = new PublishSjpCourtListRequest(
                PublishSjpCourtListRequest.SJP_PUBLISH_LIST, "ENGLISH", "FULL", listPayload);

        PublishSjpCourtListResponse response = service.publishSjpCourtList(request);

        assertThat(response.getStatus()).isEqualTo("ACCEPTED");
        assertThat(response.getMessage()).contains("no readyCases");
    }

    @Test
    void publishSjpCourtList_transformsAndSendsToCath_whenPayloadPresent() {
        SjpListPayload listPayload = new SjpListPayload(
                "2024-01-01 00:00:00",
                List.of(Map.of("caseUrn", "C1", "defendantName", "D", "sjpOffences", List.of(Map.of("title", "T"))))
        );
        PublishSjpCourtListRequest request = new PublishSjpCourtListRequest(
                PublishSjpCourtListRequest.SJP_PUBLISH_LIST, "ENGLISH", "FULL", listPayload);

        when(transformer.transform(any(), eq("SJP Public list"), eq(false))).thenReturn("{\"document\":{},\"courtLists\":[]}");
        when(publishingService.sendData(any(), any())).thenReturn(200);

        PublishSjpCourtListResponse response = service.publishSjpCourtList(request);

        assertThat(response.getStatus()).isEqualTo("ACCEPTED");
        assertThat(response.getMessage()).contains("published to CaTH");
        verify(transformer).transform(listPayload, "SJP Public list", false);
        verify(publishingService).sendData(eq("{\"document\":{},\"courtLists\":[]}"), any());
    }

    @Test
    void publishSjpCourtList_usesPressDocumentNameAndClassifiedSensitivity_whenPressList() {
        SjpListPayload listPayload = new SjpListPayload(
                "2024-01-01 00:00:00",
                List.of(Map.of("caseUrn", "C1", "sjpOffences", List.of()))
        );
        PublishSjpCourtListRequest request = new PublishSjpCourtListRequest(
                PublishSjpCourtListRequest.SJP_PRESS_LIST, "ENGLISH", "FULL", listPayload);

        when(transformer.transform(any(), eq("SJP Press list"), eq(true))).thenReturn("{}");
        when(publishingService.sendData(any(), any())).thenReturn(200);

        service.publishSjpCourtList(request);

        verify(transformer).transform(listPayload, "SJP Press list", true);
    }
}
