package uk.gov.hmcts.cp.services.crown;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrownPublishRequestTest {

    @ParameterizedTest
    @CsvSource({
            "DAILY, 2026-06-10, 2026-06-10",
            "FIRM, 2026-06-08, 2026-06-14",
            "FIRM, 2026-06-08, 2026-06-12",
            "WARNED, 2026-06-08, 2026-06-08"})
    void acceptsValidDates(CrownListType type, LocalDate startDate, LocalDate endDate) {
        assertThatCode(() -> CrownPublishRequest.validateDates(type, startDate, endDate)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
            "DAILY, 2026-06-10, 2026-06-11, must be equal",
            "FIRM, 2026-06-09, 2026-06-14, must be a Monday",
            "WARNED, 2026-06-14, 2026-06-14, must be a Monday",
            "FIRM, 2026-06-08, 2026-06-15, at most 7 days",
            "WARNED, 2026-06-08, 2026-06-07, on or after startDate"})
    void rejectsInvalidDates(CrownListType type, LocalDate startDate, LocalDate endDate, String message) {
        assertThatThrownBy(() -> CrownPublishRequest.validateDates(type, startDate, endDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(message);
    }

    @Test
    void rejectsMissingDates() {
        assertThatThrownBy(() -> CrownPublishRequest.validateDates(CrownListType.FIRM, LocalDate.of(2026, 6, 8), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }

    @Test
    void requestAppliesTheDateRules() {
        LocalDate monday = LocalDate.of(2026, 6, 8);
        assertThatThrownBy(() -> request(CrownListType.DAILY, monday, monday.plusDays(1), "325"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be equal");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void requestNeedsANumericCourtId(String courtIdNumeric) {
        LocalDate monday = LocalDate.of(2026, 6, 8);
        assertThatThrownBy(() -> request(CrownListType.DAILY, monday, monday, courtIdNumeric))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("courtIdNumeric");
    }

    @Test
    void describesTheListAndItsDates() {
        CrownPublishRequest request = request(CrownListType.FIRM, LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 14), "325");
        assertThat(request.describe()).isEqualTo("FIRM list 2026-06-08 to 2026-06-14");
    }

    private static CrownPublishRequest request(CrownListType type, LocalDate startDate, LocalDate endDate, String courtId) {
        return new CrownPublishRequest(UUID.randomUUID(), UUID.randomUUID(), type, startDate, endDate, "user-id", courtId, null);
    }
}
