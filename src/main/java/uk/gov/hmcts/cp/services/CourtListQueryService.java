package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import uk.gov.hmcts.cp.models.CourtListPayload;
import uk.gov.hmcts.cp.models.transformed.CourtListDocument;
import uk.gov.hmcts.cp.openapi.model.CourtListType;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourtListQueryService {

    /** GENESIS user ID used for reference data calls (same as document generator). */
    private static final String GENESIS_USER_ID = "7aee5dea-b0de-4604-b49b-86c7788cfc4b";
    private static final String COURT_LIST_SCHEMA_PATH = "schema/court-list-schema.json";
    private static final String PUBLIC_COURT_LIST_SCHEMA_PATH = "schema/public-court-list-schema.json";

    private final ProgressionQueryService progressionQueryService;
    private final ReferenceDataService referenceDataService;
    private final CourtListTransformationService transformationService;
    private final PublicCourtListTransformationService publicCourtListTransformationService;
    private final JsonSchemaValidatorService jsonSchemaValidatorService;

    /**
     * Transforms an existing payload into CourtListDocument (no remote fetch).
     * Use when the payload was already obtained so that getCourtListPayload is not called again.
     */
    public CourtListDocument buildCourtListDocumentFromPayload(CourtListPayload payload, CourtListType listId) {
        if (CourtListType.PUBLIC.equals(listId)) {
            log.info("Using PublicCourtListTransformationService for PUBLIC list type");
            CourtListDocument document = publicCourtListTransformationService.transform(payload);
            jsonSchemaValidatorService.validate(document, PUBLIC_COURT_LIST_SCHEMA_PATH);
            return document;
        }
        log.info("Using CourtListTransformationService for list type: {}", listId);
        CourtListDocument document = transformationService.transform(payload);
        jsonSchemaValidatorService.validate(document, COURT_LIST_SCHEMA_PATH);
        return document;
    }

    public @NotNull CourtListPayload getCourtListPayload(final CourtListType listId, final String courtCentreId, final String startDate, final String endDate, final String cjscppuid) {
        var payload = progressionQueryService.getCourtListPayload(listId, courtCentreId, startDate, endDate, cjscppuid);

        // Reference data: getCourtCenterDataByCourtName → ouCode and courtId (GENESIS user)
        if (payload.getCourtCentreName() != null && !payload.getCourtCentreName().isBlank()) {
            referenceDataService.getCourtCenterDataByCourtName(payload.getCourtCentreName(), GENESIS_USER_ID)
                    .ifPresent(courtCentre -> {
                        payload.setOuCode(courtCentre.getOuCode());
                        payload.setCourtId(courtCentre.getId() != null ? courtCentre.getId().toString() : null);
                        payload.setCourtIdNumeric(courtCentre.getCourtIdNumeric());
                    });
        }
        return payload;
    }
}

