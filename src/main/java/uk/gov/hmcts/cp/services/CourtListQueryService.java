package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.models.transformed.CourtListDocument;
import uk.gov.hmcts.cp.openapi.model.CourtListType;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourtListQueryService {

    private static final String COURT_LIST_SCHEMA_PATH = "schema/court-list-schema.json";
    private static final String PUBLIC_COURT_LIST_SCHEMA_PATH = "schema/public-court-list-schema.json";

    private final ProgressionQueryService progressionQueryService;
    private final ReferenceDataService referenceDataService;
    private final CourtListTransformationService transformationService;
    private final PublicCourtListTransformationService publicCourtListTransformationService;
    private final JsonSchemaValidatorService jsonSchemaValidatorService;

    public CourtListDocument queryCourtList(CourtListType listId, String courtCentreId, String startDate, String endDate, String cjscppuid) {
        try {
            // Fetch court list payload from progression-service
            var payload = progressionQueryService.getCourtListPayload(listId, courtCentreId, startDate, endDate, cjscppuid);

            // Reference data: getCourtCenterDataByCourtName → ouCode and courtId (aligned with progression context)
            if (payload.getCourtCentreName() != null && !payload.getCourtCentreName().isBlank()) {
                referenceDataService.getCourtCenterDataByCourtName(payload.getCourtCentreName())
                        .ifPresent(courtCentre -> {
                            payload.setOuCode(courtCentre.getOuCode());
                            payload.setCourtId(courtCentre.getId() != null ? courtCentre.getId().toString() : null);
                            payload.setCourtIdNumeric(courtCentre.getCourtIdNumeric());
                        });
            }

            // Transform to required format based on listId
            CourtListDocument document;
            if ("PUBLIC".equalsIgnoreCase(listId.name())) {
                log.info("Using PublicCourtListTransformationService for PUBLIC list type");
                document = publicCourtListTransformationService.transform(payload);
                // Validate the transformed document against JSON schema
                jsonSchemaValidatorService.validate(document, PUBLIC_COURT_LIST_SCHEMA_PATH);
            } else {
                log.info("Using CourtListTransformationService for list type: {}", listId);
                document = transformationService.transform(payload);
                // Validate the transformed document against JSON schema
                jsonSchemaValidatorService.validate(document, COURT_LIST_SCHEMA_PATH);
            }

            return document;
        } catch (Exception e) {
            log.error("Error processing court list query", e);
            throw new RuntimeException("Failed to process court list query: " + e.getMessage(), e);
        }
    }
}

