package uk.gov.hmcts.cp.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.config.AppConstant;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishRequest;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishResponse;
import uk.gov.hmcts.cp.services.crown.CrownCourtListPublishService;

/**
 * Publishes a Crown court list to CaTH in one of the Crown schemas. The schema is chosen by the court list type:
 * DRAFT and FINAL publish the daily list, WARN the advance list and FIRM the firm list.
 * <p>
 * The media type names the action for the authorization rules (courtlistpublishing-service.crown.post).
 */
@RestController
public class CrownCourtListPublishController {

    public static final String PATH = "/api/court-list-publish/crown/publish";
    public static final String MEDIA_TYPE = "application/vnd.courtlistpublishing-service.crown.post+json";

    private static final Logger LOG = LoggerFactory.getLogger(CrownCourtListPublishController.class);

    private final CrownCourtListPublishService service;

    public CrownCourtListPublishController(final CrownCourtListPublishService service) {
        this.service = service;
    }

    @PostMapping(path = PATH, consumes = MEDIA_TYPE, produces = MEDIA_TYPE)
    public ResponseEntity<CourtListPublishResponse> publishCrownCourtList(
            @RequestBody final CourtListPublishRequest request,
            @RequestHeader(value = AppConstant.CJSCPPUID, required = false) final String userId) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CJSCPPUID header is required");
        }
        LOG.atInfo().log("Crown court list publish requested for court centre ID: {}, type: {}, startDate: {}, endDate: {}",
                request.getCourtCentreId(), request.getCourtListType(), request.getStartDate(), request.getEndDate());

        final CourtListPublishResponse response = service.publish(
                request.getCourtCentreId(), request.getCourtListType(), request.getStartDate(), request.getEndDate(), userId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(MEDIA_TYPE))
                .body(response);
    }
}
