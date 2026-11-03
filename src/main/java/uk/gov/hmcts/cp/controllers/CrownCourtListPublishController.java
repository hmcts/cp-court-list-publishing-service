package uk.gov.hmcts.cp.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.config.AppConstant;
import uk.gov.hmcts.cp.openapi.api.CrownCourtListPublishApi;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishRequest;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishResponse;
import uk.gov.hmcts.cp.services.crown.CrownCourtListPublishService;

/**
 * Publishes a Crown court list to CaTH in one of the Crown schemas. The path, media type and documentation come from
 * the generated {@link CrownCourtListPublishApi} (api-cp-crime-court-list-publisher). The court list type selects the
 * schema: FINAL publishes the daily list, WARN the advance list and FIRM the firm list; a DRAFT is not published.
 * <p>
 * The media type names the action for the authorization rules (courtlistpublishing-service.crown.post).
 */
@RestController
public class CrownCourtListPublishController implements CrownCourtListPublishApi {

    public static final String MEDIA_TYPE = "application/vnd.courtlistpublishing-service.crown.post+json";

    private static final Logger LOG = LoggerFactory.getLogger(CrownCourtListPublishController.class);

    private final CrownCourtListPublishService service;

    public CrownCourtListPublishController(final CrownCourtListPublishService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<CourtListPublishResponse> publishCrownCourtList(final CourtListPublishRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        final String userId = requireCjscppuid();
        LOG.atInfo().log("Crown court list publish requested for court centre ID: {}, type: {}, startDate: {}, endDate: {}",
                request.getCourtCentreId(), request.getCourtListType(), request.getStartDate(), request.getEndDate());

        final CourtListPublishResponse response = service.publish(
                request.getCourtCentreId(), request.getCourtListType(), request.getStartDate(), request.getEndDate(), userId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(MEDIA_TYPE))
                .body(response);
    }

    /** Returns the caller's CJSCPPUID, or throws 400 when the header is absent. */
    private static String requireCjscppuid() {
        final String userId = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                ? attrs.getRequest().getHeader(AppConstant.CJSCPPUID)
                : null;
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CJSCPPUID header is required");
        }
        return userId;
    }
}
