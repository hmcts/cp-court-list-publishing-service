package uk.gov.hmcts.cp.services.crown;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.services.PublicationSchema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrownListTypeTest {

    @Test
    void draftAndFinalSelectTheDailyList() {
        assertThat(CrownListType.from(CourtListType.DRAFT)).isEqualTo(CrownListType.DAILY);
        assertThat(CrownListType.from(CourtListType.FINAL)).isEqualTo(CrownListType.DAILY);
    }

    @Test
    void warnSelectsTheAdvanceListAndFirmTheFirmList() {
        assertThat(CrownListType.from(CourtListType.WARN)).isEqualTo(CrownListType.ADVANCE);
        assertThat(CrownListType.from(CourtListType.FIRM)).isEqualTo(CrownListType.FIRM);
    }

    @Test
    void everyOtherCourtListTypeIsRejected() {
        for (final CourtListType type : CourtListType.values()) {
            if (type == CourtListType.DRAFT || type == CourtListType.FINAL || type == CourtListType.WARN || type == CourtListType.FIRM) {
                assertThat(CrownListType.isSupported(type)).as(type.name()).isTrue();
            } else {
                assertThat(CrownListType.isSupported(type)).as(type.name()).isFalse();
                assertThatThrownBy(() -> CrownListType.from(type))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining(type.name());
            }
        }
    }

    @Test
    void aMissingCourtListTypeIsRejected() {
        assertThatThrownBy(() -> CrownListType.from(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(CrownListType.isSupported(null)).isFalse();
    }

    @Test
    void eachListTypeHasItsCathListTypeSchemaRootElementAndDocumentType() {
        assertThat(CrownListType.DAILY.cathListType()).isEqualTo("CROWN_DAILY_LIST");
        assertThat(CrownListType.DAILY.schema()).isEqualTo(PublicationSchema.CROWN_DAILY);
        assertThat(CrownListType.DAILY.rootElement()).isEqualTo("DailyList");
        assertThat(CrownListType.DAILY.documentType()).isEqualTo("DL");

        assertThat(CrownListType.ADVANCE.cathListType()).isEqualTo("CROWN_ADVANCE_LIST");
        assertThat(CrownListType.ADVANCE.schema()).isEqualTo(PublicationSchema.CROWN_ADVANCE);
        assertThat(CrownListType.ADVANCE.rootElement()).as("the advance schema's root is WarnedList").isEqualTo("WarnedList");
        assertThat(CrownListType.ADVANCE.documentType()).isEqualTo("WL");

        assertThat(CrownListType.FIRM.cathListType()).isEqualTo("CROWN_FIRM_LIST");
        assertThat(CrownListType.FIRM.schema()).isEqualTo(PublicationSchema.CROWN_FIRM);
        assertThat(CrownListType.FIRM.rootElement()).isEqualTo("FirmList");
        assertThat(CrownListType.FIRM.documentType()).isEqualTo("FL");
    }

    @Test
    void onlyAdvanceAndFirmListsAreWeekCommencing() {
        assertThat(CrownListType.DAILY.isWeekCommencing()).isFalse();
        assertThat(CrownListType.ADVANCE.isWeekCommencing()).isTrue();
        assertThat(CrownListType.FIRM.isWeekCommencing()).isTrue();
    }
}
