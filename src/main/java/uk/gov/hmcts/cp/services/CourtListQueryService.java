package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.models.CourtListPayload;
import uk.gov.hmcts.cp.models.transformed.CourtListDocument;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourtListQueryService {

    private final ListingQueryService listingQueryService;
    private final CourtListTransformationService transformationService;
    private final PublicCourtListTransformationService publicCourtListTransformationService;

    public CourtListDocument queryCourtList(String listId, String courtCentreId, String courtRoomId, String startDate, String endDate, String cjscppuid) {
        try {
            // Fetch data from common-platform-query-api
            var payload = listingQueryService.getCourtListPayload(listId, courtCentreId, courtRoomId, startDate, endDate, cjscppuid);

            // Transform to required format based on listId
            CourtListDocument document;
            if ("PUBLIC".equalsIgnoreCase(listId)) {
                log.info("Using PublicCourtListTransformationService for PUBLIC list type");
                document = publicCourtListTransformationService.transform(payload);
            } else {
                log.info("Using CourtListTransformationService for list type: {}", listId);
                document = transformationService.transform(payload);
            }

            return document;
        } catch (Exception e) {
            log.error("Error processing court list query", e);
            throw new RuntimeException("Failed to process court list query: " + e.getMessage(), e);
        }
    }

    public CourtListPayload queryCourtListResponse(String listId, String courtCentreId, String courtRoomId, String startDate, String endDate, String cjscppuid) {
        try {
            // Fetch data from common-platform-query-api
            return listingQueryService.getCourtListPayload(listId, courtCentreId, courtRoomId, startDate, endDate, cjscppuid);
        } catch (Exception e) {
            log.error("Error processing court list query", e);
            throw new RuntimeException("Failed to process court list query: " + e.getMessage(), e);
        }
    }
}

