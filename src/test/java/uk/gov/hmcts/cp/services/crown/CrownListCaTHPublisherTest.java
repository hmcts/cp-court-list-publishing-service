package uk.gov.hmcts.cp.services.crown;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;
import uk.gov.hmcts.cp.domain.DtsMeta;
import uk.gov.hmcts.cp.services.AzureBlobService;
import uk.gov.hmcts.cp.services.CourtListPublisher;
import uk.gov.hmcts.cp.services.JsonSchemaValidatorService;
import uk.gov.hmcts.cp.services.PublicationSchema;
import uk.gov.hmcts.cp.services.SchemaValidationException;
import uk.gov.hmcts.cp.services.sanitization.DocumentSanitizer;
import uk.gov.hmcts.cp.services.sanitization.HtmlStrippingSanitizer;
import uk.gov.hmcts.cp.services.sanitization.RequiredStringFieldsRegistry;
import uk.gov.hmcts.cp.services.sanitization.WafPatternSanitizer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrownListCaTHPublisherTest {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperConfig.getObjectMapper();
    private static final LocalDate MONDAY = LocalDate.of(2026, 6, 8);
    private static final LocalDate TUESDAY = LocalDate.of(2026, 6, 9);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 6, 14);

    @Mock
    private CrownListDataClient dataClient;
    @Mock
    private CourtListPublisher courtListPublisher;
    @Mock
    private AzureBlobService azureBlobService;

    private String listingExample;
    private UUID courtListId;

    @BeforeEach
    void setUp() throws IOException {
        courtListId = UUID.randomUUID();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("crown/listing-crown-list-payload.json")) {
            listingExample = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "DAILY, 2026-06-09, 2026-06-09, CROWN_DAILY_PDDA_LIST, DailyList, 2026-06-09T23:59:00Z",
            "FIRM, 2026-06-08, 2026-06-14, CROWN_FIRM_PDDA_LIST, FirmList, 2026-06-14T23:59:00Z",
            "WARNED, 2026-06-08, 2026-06-14, CROWN_ADVANCE_PDDA_LIST, WarnedList, 2026-06-14T23:59:00Z"})
    void publishesTheValidatedListToCaTH(CrownListType type, LocalDate startDate, LocalDate endDate,
                                         String cathListType, String rootElement, String displayTo) throws IOException {
        CrownPublishRequest request = request(type, startDate, endDate, "02920 345678");
        when(dataClient.fetch(request)).thenReturn(listingExample);
        when(courtListPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(200);

        publisher(new JsonSchemaValidatorService()).publish(request);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<DtsMeta> meta = ArgumentCaptor.forClass(DtsMeta.class);
        verify(courtListPublisher).publish(body.capture(), meta.capture());
        JsonNode document = OBJECT_MAPPER.readTree(body.getValue());
        assertThat(document.at("/" + rootElement + "/DocumentID/UniqueID").asText()).isEqualTo(courtListId.toString());
        assertThat(document.at("/" + rootElement + "/CrownCourt/CourtHouseTelephone").asText()).isEqualTo("02920 345678");

        DtsMeta dtsMeta = meta.getValue();
        assertThat(dtsMeta.getProvenance()).isEqualTo("COMMON_PLATFORM");
        assertThat(dtsMeta.getType()).isEqualTo("LIST");
        assertThat(dtsMeta.getListType()).isEqualTo(cathListType);
        assertThat(dtsMeta.getCourtId()).isEqualTo("325");
        assertThat(dtsMeta.getLanguage()).isEqualTo("WELSH");
        assertThat(dtsMeta.getSensitivity()).isEqualTo(CrownListCaTHPublisher.SENSITIVITY);
        assertThat(dtsMeta.getContentDate()).startsWith(startDate + "T");
        assertThat(dtsMeta.getDisplayTo()).isEqualTo(displayTo);
        verify(azureBlobService).uploadJson(body.getValue(), courtListId + "-cath.json");
    }

    @Test
    void textFromListingAndTheCourtTelephoneIsCleaned() throws IOException {
        CrownPublishRequest request = request(CrownListType.DAILY, TUESDAY, TUESDAY, "<b>02920</b> ../345678");
        when(dataClient.fetch(request)).thenReturn(listingExample.replace("Court 1 sits late today", "<i>Court 1</i> sits ../late"));
        when(courtListPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(200);

        publisher(new JsonSchemaValidatorService()).publish(request);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(courtListPublisher).publish(body.capture(), any());
        JsonNode list = OBJECT_MAPPER.readTree(body.getValue()).get("DailyList");
        assertThat(list.at("/CrownCourt/CourtHouseTelephone").asText()).isEqualTo("02920 345678");
        assertThat(list.at("/CourtLists/0/Sittings/0/SittingNote").asText()).isEqualTo("Court 1 sits late");
    }

    @Test
    void doesNotSendAnEmptyListWhenNoneOfListingsHearingsCanBePublished() {
        // the example's hearings are all on 9 June, so none falls on the requested day
        CrownPublishRequest request = request(CrownListType.DAILY, MONDAY, MONDAY, null);
        when(dataClient.fetch(request)).thenReturn(listingExample);

        assertThatThrownBy(() -> publisher(new JsonSchemaValidatorService()).publish(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("None of the 2 hearings");
        verify(courtListPublisher, never()).publish(anyString(), any());
    }

    @Test
    void publishesAnEmptyListWhenListingHasNoHearings() {
        CrownPublishRequest request = request(CrownListType.WARNED, MONDAY, SUNDAY, null);
        when(dataClient.fetch(request)).thenReturn("{\"courtCentreName\":\"Leeds Crown Court\",\"courtLists\":[]}");
        when(courtListPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(200);

        publisher(new JsonSchemaValidatorService()).publish(request);

        verify(courtListPublisher).publish(anyString(), any(DtsMeta.class));
    }

    @Test
    void failsWhenCaTHReturnsAnErrorStatus() {
        CrownPublishRequest request = request(CrownListType.DAILY, TUESDAY, TUESDAY, null);
        when(dataClient.fetch(request)).thenReturn(listingExample);
        when(courtListPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(500);
        CrownListCaTHPublisher publisher = publisher(new JsonSchemaValidatorService());

        assertThatThrownBy(() -> publisher.publish(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CaTH returned status 500");
    }

    @Test
    void doesNotPublishWhenTheDocumentFailsSchemaValidation() {
        CrownPublishRequest request = request(CrownListType.DAILY, TUESDAY, TUESDAY, null);
        when(dataClient.fetch(request)).thenReturn(listingExample);
        JsonSchemaValidatorService failingValidator = mock(JsonSchemaValidatorService.class);
        doThrow(new SchemaValidationException("JSON schema validation failed"))
                .when(failingValidator).validate(anyString(), any(PublicationSchema.class));
        CrownListCaTHPublisher publisher = publisher(failingValidator);

        assertThatThrownBy(() -> publisher.publish(request)).isInstanceOf(SchemaValidationException.class);
        verify(courtListPublisher, never()).publish(anyString(), any());
    }

    @Test
    void publishesEvenWhenTheBlobUploadFails() {
        CrownPublishRequest request = request(CrownListType.DAILY, TUESDAY, TUESDAY, null);
        when(dataClient.fetch(request)).thenReturn(listingExample);
        when(courtListPublisher.publish(anyString(), any(DtsMeta.class))).thenReturn(200);
        doThrow(new RuntimeException("blob down")).when(azureBlobService).uploadJson(anyString(), anyString());

        publisher(new JsonSchemaValidatorService()).publish(request);

        verify(courtListPublisher).publish(anyString(), any(DtsMeta.class));
    }

    @Test
    void failsWhenListingDoesNotReturnJson() {
        CrownPublishRequest request = request(CrownListType.WARNED, MONDAY, SUNDAY, null);
        when(dataClient.fetch(request)).thenReturn("not json");
        CrownListCaTHPublisher publisher = publisher(new JsonSchemaValidatorService());

        assertThatThrownBy(() -> publisher.publish(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not JSON");
    }

    private CrownPublishRequest request(CrownListType type, LocalDate startDate, LocalDate endDate, String telephone) {
        return new CrownPublishRequest(courtListId, UUID.randomUUID(), type, startDate, endDate, "user-id", "325", telephone);
    }

    private CrownListCaTHPublisher publisher(JsonSchemaValidatorService validator) {
        WafPatternSanitizer waf = new WafPatternSanitizer("..\\,../");
        HtmlStrippingSanitizer html = new HtmlStrippingSanitizer();
        return new CrownListCaTHPublisher(dataClient, new CrownListTransformer(),
                new DocumentSanitizer(waf, html, new RequiredStringFieldsRegistry()), waf, html, validator,
                courtListPublisher, Optional.of(azureBlobService));
    }
}
