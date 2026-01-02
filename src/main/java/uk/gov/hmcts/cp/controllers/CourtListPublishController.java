package uk.gov.hmcts.cp.controllers;

import uk.gov.hmcts.cp.dto.CourtListPublishRequest;
import uk.gov.hmcts.cp.dto.CourtListPublishResponse;
import uk.gov.hmcts.cp.services.CourtListPublishStatusService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(path = "api/court-list-publish", produces = MediaType.APPLICATION_JSON_VALUE)
public class CourtListPublishController {

    private final CourtListPublishStatusService service;
    private static final Logger LOG = LoggerFactory.getLogger(CourtListPublishController.class);

    public CourtListPublishController(final CourtListPublishStatusService service) {
        this.service = service;
    }

    @PostMapping("/publish")
    public ResponseEntity<CourtListPublishResponse> publishCourtList(
            @RequestBody final CourtListPublishRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        
        LOG.atInfo().log("Creating or updating court list publish status for court list ID: {}",
                request.courtListId());

        final CourtListPublishResponse response = service.createOrUpdate(
                request.courtListId(),
                request.courtCentreId(),
                request.publishStatus(),
                request.courtListType()
        );

        return ResponseEntity.ok()
                .contentType(new MediaType("application", "vnd.courtlistpublishing-service.publish.post+json"))
                .body(response);
    }

    @GetMapping("/court-centre/{courtCentreId}")
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

