package uk.gov.hmcts.cp.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.api.sjp.PublishSjpCourtListRequest;
import uk.gov.hmcts.cp.api.sjp.PublishSjpCourtListResponse;
import uk.gov.hmcts.cp.services.sjp.SjpCourtListPublishService;

import org.springframework.http.HttpStatus;

import jakarta.validation.Valid;

/**
 * Exposes POST /api/court-list-publish/publishCourtList for publishing SJP court lists to CaTH.
 * Request parameter listType: SJP_PUBLISH_LIST or SJP_PRESS_LIST.
 */
@RestController
@RequestMapping("/api/court-list-publish")
public class SjpCourtListPublishController {

    private static final Logger LOG = LoggerFactory.getLogger(SjpCourtListPublishController.class);
    private static final MediaType VND_PUBLISHCOURTLIST_JSON =
            new MediaType("application", "vnd.courtlistpublishing-service.publishcourtlist.post+json");

    private final SjpCourtListPublishService sjpCourtListPublishService;

    public SjpCourtListPublishController(SjpCourtListPublishService sjpCourtListPublishService) {
        this.sjpCourtListPublishService = sjpCourtListPublishService;
    }

    @PostMapping(
            value = "/publishCourtList",
            consumes = {MediaType.APPLICATION_JSON_VALUE, "application/vnd.courtlistpublishing-service.publishcourtlist.post+json"},
            produces = {MediaType.APPLICATION_JSON_VALUE, "application/vnd.courtlistpublishing-service.publishcourtlist.post+json"}
    )
    public ResponseEntity<PublishSjpCourtListResponse> publishCourtList(
            @RequestBody @Valid PublishSjpCourtListRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (request.getListType() == null || request.getListType().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "listType is required");
        }
        String listType = request.getListType().trim();
        if (!PublishSjpCourtListRequest.SJP_PUBLISH_LIST.equals(listType)
                && !PublishSjpCourtListRequest.SJP_PRESS_LIST.equals(listType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "listType must be SJP_PUBLISH_LIST or SJP_PRESS_LIST");
        }

        LOG.debug("Publishing SJP court list, listType={}", listType);
        PublishSjpCourtListResponse response = sjpCourtListPublishService.publishSjpCourtList(request);
        return ResponseEntity.ok()
                .contentType(VND_PUBLISHCOURTLIST_JSON)
                .body(response);
    }
}
