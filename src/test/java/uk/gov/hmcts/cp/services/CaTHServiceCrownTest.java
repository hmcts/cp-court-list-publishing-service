package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.domain.DtsMeta;
import uk.gov.hmcts.cp.services.crown.CrownListType;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaTHServiceCrownTest {

    private static final UUID COURT_LIST_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final LocalDate START = LocalDate.parse("2026-11-02");
    private static final LocalDate END = LocalDate.parse("2026-11-08");
    private static final String DOCUMENT = "{\"WarnedList\":{}}";

    @Mock
    private CourtListPublisher publisher;
    @Mock
    private AzureBlobService azureBlobService;

    private CaTHService cathService;

    @BeforeEach
    void setUp() {
        cathService = new CaTHService(publisher, Optional.of(azureBlobService));
    }

    @Test
    void sendsTheDocumentUnchangedWithTheCathListTypeAndTheListsDates() {
        when(publisher.publish(anyString(), any(DtsMeta.class))).thenReturn(200);

        cathService.sendCrownListToCaTH(DOCUMENT, CrownListType.ADVANCE, START, END, "457", COURT_LIST_ID);

        final ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<DtsMeta> meta = ArgumentCaptor.forClass(DtsMeta.class);
        verify(publisher).publish(payload.capture(), meta.capture());
        assertThat(payload.getValue()).isEqualTo(DOCUMENT);
        assertThat(meta.getValue().getProvenance()).isEqualTo("COMMON_PLATFORM");
        assertThat(meta.getValue().getType()).isEqualTo("LIST");
        assertThat(meta.getValue().getListType()).isEqualTo("CROWN_ADVANCE_LIST");
        assertThat(meta.getValue().getCourtId()).isEqualTo("457");
        assertThat(meta.getValue().getLanguage()).isEqualTo("ENGLISH");
        assertThat(meta.getValue().getSensitivity()).isEqualTo("CLASSIFIED");
        assertThat(meta.getValue().getContentDate()).startsWith("2026-11-02");
        assertThat(meta.getValue().getDisplayTo()).as("shown until the end of the last day").isEqualTo("2026-11-08T23:59:00Z");
    }

    @Test
    void usesTheCathListTypeOfEachCrownList() {
        when(publisher.publish(anyString(), any(DtsMeta.class))).thenReturn(200);

        cathService.sendCrownListToCaTH(DOCUMENT, CrownListType.DAILY, START, START, "457", COURT_LIST_ID);
        cathService.sendCrownListToCaTH(DOCUMENT, CrownListType.FIRM, START, END, "457", COURT_LIST_ID);

        final ArgumentCaptor<DtsMeta> meta = ArgumentCaptor.forClass(DtsMeta.class);
        verify(publisher, org.mockito.Mockito.times(2)).publish(anyString(), meta.capture());
        assertThat(meta.getAllValues().get(0).getListType()).isEqualTo("CROWN_DAILY_LIST");
        assertThat(meta.getAllValues().get(1).getListType()).isEqualTo("CROWN_FIRM_LIST");
    }

    @Test
    void usesCourtIdZeroWhenTheCourtHasNoNumericId() {
        when(publisher.publish(anyString(), any(DtsMeta.class))).thenReturn(200);

        cathService.sendCrownListToCaTH(DOCUMENT, CrownListType.DAILY, START, START, null, COURT_LIST_ID);

        final ArgumentCaptor<DtsMeta> meta = ArgumentCaptor.forClass(DtsMeta.class);
        verify(publisher).publish(anyString(), meta.capture());
        assertThat(meta.getValue().getCourtId()).isEqualTo("0");
    }

    @Test
    void keepsACopyOfTheDocumentInBlobStorage() {
        when(publisher.publish(anyString(), any(DtsMeta.class))).thenReturn(200);

        cathService.sendCrownListToCaTH(DOCUMENT, CrownListType.DAILY, START, START, "457", COURT_LIST_ID);

        verify(azureBlobService).uploadJson(DOCUMENT, CaTHService.buildBlobName(COURT_LIST_ID));
    }

    @Test
    void wrapsAPublishFailure() {
        when(publisher.publish(anyString(), any(DtsMeta.class))).thenThrow(new IllegalStateException("APIM down"));

        assertThatThrownBy(() -> cathService.sendCrownListToCaTH(DOCUMENT, CrownListType.DAILY, START, START, "457", COURT_LIST_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to send Crown list to CaTH")
                .hasMessageContaining("APIM down");
    }
}
