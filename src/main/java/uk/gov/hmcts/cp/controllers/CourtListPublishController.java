package uk.gov.hmcts.cp.controllers;

import uk.gov.hmcts.cp.domain.CourtListPublishStatusEntity;
import uk.gov.hmcts.cp.dto.CourtListPublishRequest;
import uk.gov.hmcts.cp.dto.CourtListPublishResponse;
import uk.gov.hmcts.cp.services.CourtListPublishStatusService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(path = "api/court-list-publish", produces = MediaType.APPLICATION_JSON_VALUE)
public class CourtListPublishController {

    private final CourtListPublishStatusService service;
    private static final Logger LOG = LoggerFactory.getLogger(CourtListPublishController.class);

    public CourtListPublishController(final CourtListPublishStatusService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CourtListPublishResponse> publishCourtList(
            @RequestBody CourtListPublishRequest request) {
        return createOrUpdateCourtList(request, HttpStatus.CREATED);
    }

    @PutMapping
    public ResponseEntity<CourtListPublishResponse> updatePublishedCourtList(
            @RequestBody CourtListPublishRequest request) {
        return createOrUpdateCourtList(request, HttpStatus.OK);
    }

    private ResponseEntity<CourtListPublishResponse> createOrUpdateCourtList(
            CourtListPublishRequest request,
            HttpStatus status) {
        LOG.atInfo().log("Creating or updating court list publish status for court list ID: {}", 
                request.courtListId());
        
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        CourtListPublishStatusEntity entity = service.createOrUpdate(
                request.courtListId(),
                request.courtCentreId(),
                request.publishStatus(),
                request.courtListType()
        );

        CourtListPublishResponse response = CourtListPublishResponse.from(entity);

        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }
}

