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

    private static final String STANDARD_COURT_LIST_SCHEMA_JSON = "schema/standard-court-list-schema.json";
    private static final String ONLINE_PUBLIC_COURT_LIST_SCHEMA_JSON = "schema/online-public-court-list-schema.json";

    private final CourtListDataService courtListDataService;
    private final StandardCourtListTransformationService transformationService;
    private final OnlinePublicCourtListTransformationService onlinePublicCourtListTransformationService;
    private final JsonSchemaValidatorService jsonSchemaValidatorService;

    /**
     * Transforms an existing payload into CourtListDocument (no remote fetch).
     * Use when the payload was already obtained so that getCourtListPayload is not called again.
     */
    public CourtListDocument buildCourtListDocumentFromPayload(CourtListPayload payload, CourtListType listId) {
        if (CourtListType.ONLINE_PUBLIC.equals(listId)) {
            log.info("Using PublicCourtListTransformationService for PUBLIC list type");
            CourtListDocument document = onlinePublicCourtListTransformationService.transform(payload);
            jsonSchemaValidatorService.validate(document, ONLINE_PUBLIC_COURT_LIST_SCHEMA_JSON);
            return document;
        }
        log.info("Using CourtListTransformationService for list type: {}", listId);
        CourtListDocument document = transformationService.transform(payload);
        jsonSchemaValidatorService.validate(document, STANDARD_COURT_LIST_SCHEMA_JSON);
        return document;
    }

    public @NotNull CourtListPayload getCourtListPayload(final CourtListType listId, final String courtCentreId, final String startDate, final String endDate, final String cjscppuid) {
        return courtListDataService.getCourtListPayload(listId, courtCentreId, startDate, endDate, cjscppuid);
    }
}

