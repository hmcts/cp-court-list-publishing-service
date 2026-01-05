package uk.gov.hmcts.cp.controllers;

import uk.gov.hmcts.cp.openapi.api.CourtListPublishApi;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishRequest;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishResponse;
import uk.gov.hmcts.cp.openapi.model.PublishStatus;
import uk.gov.hmcts.cp.services.CourtListPublishStatusService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
public class CourtListPublishController implements CourtListPublishApi {

    private final CourtListPublishStatusService service;
    private static final Logger LOG = LoggerFactory.getLogger(CourtListPublishController.class);

    public CourtListPublishController(final CourtListPublishStatusService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<CourtListPublishResponse> publishCourtList(
            @RequestBody final CourtListPublishRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        
        // Generate courtListId internally
        final UUID courtListId = UUID.randomUUID();
        
        // Set initial publishStatus to COURT_LIST_REQUESTED
        final PublishStatus publishStatus = PublishStatus.COURT_LIST_REQUESTED;
        
        LOG.atInfo().log("Creating court list publish status with generated court list ID: {} and initial status: {}",
                courtListId, publishStatus);

        final CourtListPublishResponse response = service.createOrUpdate(
                courtListId,
                request.getCourtCentreId(),
                publishStatus,
                request.getCourtListType()
        );

        return ResponseEntity.ok()
                .contentType(new MediaType("application", "vnd.courtlistpublishing-service.publish.post+json"))
                .body(response);
    }

    @Override
    @SuppressWarnings("unused") // Method is used by Spring's request mapping
    public ResponseEntity<List<CourtListPublishResponse>> findCourtListPublishByCourtCenterId(
            @PathVariable final UUID courtCentreId) {
        LOG.atInfo().log("Fetching court list publish statuses for court centre ID: {}", courtCentreId);
        final List<CourtListPublishResponse> responses = service.findByCourtCentreId(courtCentreId);
        return ResponseEntity.ok()
                .contentType(new MediaType("application", "vnd.courtlistpublishing-service.publish.get+json"))
                .body(responses);
    }
}

