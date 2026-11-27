package uk.gov.hmcts.cp.services.crown;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * One crown court list to publish to CaTH. The list rules live here, so the controller, the task and the
 * publisher apply the same ones:
 * <ul>
 *   <li>a daily list (FINAL) covers one day: the start and end dates are equal;</li>
 *   <li>a firm (FIRM) or warned (WARN) list is a week commencing list: it starts on a Monday and covers at
 *   most seven days.</li>
 * </ul>
 *
 * @param courtListId    the publish status row
 * @param courtIdNumeric the numeric court id CaTH files the list under (reference data)
 * @param courtTelephone court centre telephone (reference data), may be null
 */
public record CrownPublishRequest(UUID courtListId, UUID courtCentreId, CrownListType listType,
                                  LocalDate startDate, LocalDate endDate, String userId,
                                  String courtIdNumeric, String courtTelephone) {

    static final long MAX_WEEK_COMMENCING_DAYS = 7;

    public CrownPublishRequest {
        Objects.requireNonNull(courtListId, "courtListId");
        Objects.requireNonNull(courtCentreId, "courtCentreId");
        Objects.requireNonNull(userId, "userId");
        if (courtIdNumeric == null || courtIdNumeric.isBlank()) {
            throw new IllegalArgumentException("courtIdNumeric is required");
        }
        validateDates(listType, startDate, endDate);
    }

    /**
     * Checks the list dates.
     *
     * @throws IllegalArgumentException with a message fit to return to the caller
     */
    public static void validateDates(CrownListType listType, LocalDate startDate, LocalDate endDate) {
        Objects.requireNonNull(listType, "listType");
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate and endDate are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must be on or after startDate");
        }
        String name = listType.courtListType().name();
        if (!listType.weekCommencing()) {
            if (!startDate.equals(endDate)) {
                throw new IllegalArgumentException("startDate and endDate must be equal for a " + name + " list");
            }
            return;
        }
        if (startDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("A " + name + " list is a week commencing list, so startDate must be a Monday");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) >= MAX_WEEK_COMMENCING_DAYS) {
            throw new IllegalArgumentException("A " + name + " list covers at most " + MAX_WEEK_COMMENCING_DAYS + " days");
        }
    }

    /** E.g. "FIRM list 2026-06-08 to 2026-06-14", for logs and recorded errors. */
    public String describe() {
        return listType.courtListType() + " list " + startDate + " to " + endDate;
    }
}
