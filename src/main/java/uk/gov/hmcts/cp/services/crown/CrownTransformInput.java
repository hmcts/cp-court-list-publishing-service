package uk.gov.hmcts.cp.services.crown;

import com.fasterxml.jackson.databind.JsonNode;
import uk.gov.hmcts.cp.models.CourtCentreData;

import java.time.LocalDate;

/**
 * What a Crown transformer works from.
 *
 * @param payload     the listing context's court list payload for the Crown media type
 * @param courtCentre reference data for the court centre; may be null when it could not be read
 * @param startDate   first day of the list (the week commencing date for advance and firm lists)
 * @param endDate     last day of the list
 */
public record CrownTransformInput(JsonNode payload, CourtCentreData courtCentre, LocalDate startDate, LocalDate endDate) {
}
