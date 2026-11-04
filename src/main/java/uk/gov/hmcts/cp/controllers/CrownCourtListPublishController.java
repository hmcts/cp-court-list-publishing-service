package uk.gov.hmcts.cp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.config.AppConstant;
import uk.gov.hmcts.cp.models.CourtCentreData;
import uk.gov.hmcts.cp.openapi.api.CrownCourtListPublishApi;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishRequest;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishResponse;
import uk.gov.hmcts.cp.services.CourtListStatusUpdater;
import uk.gov.hmcts.cp.services.ReferenceDataService;
import uk.gov.hmcts.cp.services.crown.CrownListStatusService;
import uk.gov.hmcts.cp.services.crown.CrownListTaskTrigger;
import uk.gov.hmcts.cp.services.crown.CrownListType;
import uk.gov.hmcts.cp.services.crown.CrownPublishInProgressException;
import uk.gov.hmcts.cp.services.crown.CrownPublishRequest;

import static uk.gov.hmcts.cp.config.AppConstant.ALERT_PATTERN;

/**
 * {@code POST /api/court-list-publish/crown/publish}: publishes a Crown Court list to CaTH as a PDDA list
 * (FINAL → daily list, FIRM → firm list, WARN → advance/warned list).
 *
 * <p>Checks the request (list type, dates per {@link CrownPublishRequest}, and that the court centre is a Crown
 * Court with a numeric court id in reference data), records the publish as REQUESTED and queues the task that
 * publishes it. Every refusal, including a publish of the same list already in progress, is a 400: the API
 * contract (spec 0.3.7) declares 200, 400 and 500 for this operation.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class CrownCourtListPublishController implements CrownCourtListPublishApi {

    static final MediaType CROWN_PUBLISH_MEDIA_TYPE =
            new MediaType("application", "vnd.courtlistpublishing-service.crown.post+json");
    private static final String CROWN_COURT_OU_LEVEL_1_CODE = "C";

    private final CrownListStatusService statusService;
    private final CourtListStatusUpdater statusUpdater;
    private final CrownListTaskTrigger taskTrigger;
    private final ReferenceDataService referenceDataService;

    @Override
    public ResponseEntity<CourtListPublishResponse> publishCrownCourtList(@RequestBody final CourtListPublishRequest request) {
        if (request == null) {
            throw badRequest("Request body is required");
        }
        final String userId = cjscppuid();
        final CrownListType listType = CrownListType.from(request.getCourtListType())
                .orElseThrow(() -> badRequest("courtListType " + request.getCourtListType()
                        + " cannot be published as a crown list; use FINAL, FIRM or WARN"));
        try {
            CrownPublishRequest.validateDates(listType, request.getStartDate(), request.getEndDate());
        } catch (IllegalArgumentException e) {
            throw badRequest(e.getMessage());
        }
        if (request.getCourtCentreId() == null) {
            throw badRequest("courtCentreId is required");
        }
        final CourtCentreData courtCentre = crownCourtCentre(request.getCourtCentreId().toString(), userId);

        final CourtListPublishResponse response;
        try {
            response = statusService.requestPublish(request.getCourtCentreId(), request.getCourtListType(), request.getStartDate());
        } catch (CrownPublishInProgressException e) {
            throw badRequest(e.getMessage());
        }

        final CrownPublishRequest publish = new CrownPublishRequest(response.getCourtListId(), request.getCourtCentreId(),
                listType, request.getStartDate(), request.getEndDate(), userId,
                courtCentre.getCourtIdNumeric(), courtCentre.getPhone());
        try {
            taskTrigger.queue(publish);
        } catch (Exception e) {
            // the status row is already saved: record the failure, so it does not stay REQUESTED with no task
            log.error("Error {} queuing crown list publish, {}, courtListId={}", ALERT_PATTERN, publish.describe(),
                    response.getCourtListId(), e);
            statusUpdater.markPublishFailed(response.getCourtListId(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to queue the crown list publish", e);
        }
        log.info("Crown list publish requested, {}, courtListId={}", publish.describe(), response.getCourtListId());
        return ResponseEntity.ok().contentType(CROWN_PUBLISH_MEDIA_TYPE).body(response);
    }

    /** The court centre's reference data: it must be a Crown Court with the numeric court id CaTH files lists under. */
    private CourtCentreData crownCourtCentre(String courtCentreId, String userId) {
        final CourtCentreData courtCentre = referenceDataService.getCourtCenterDataByCourtCentreId(courtCentreId, userId)
                .orElseThrow(() -> badRequest("Court centre " + courtCentreId + " was not found in reference data"));
        if (!CROWN_COURT_OU_LEVEL_1_CODE.equalsIgnoreCase(courtCentre.getOucodeL1Code())) {
            throw badRequest("Court centre " + courtCentreId + " is not a Crown Court");
        }
        if (courtCentre.getCourtIdNumeric() == null || courtCentre.getCourtIdNumeric().isBlank()) {
            throw badRequest("Court centre " + courtCentreId + " has no numeric court id in reference data");
        }
        return courtCentre;
    }

    private static String cjscppuid() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            final String userId = attributes.getRequest().getHeader(AppConstant.CJSCPPUID);
            if (userId != null && !userId.isBlank()) {
                return userId;
            }
        }
        throw badRequest("CJSCPPUID header is required");
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
