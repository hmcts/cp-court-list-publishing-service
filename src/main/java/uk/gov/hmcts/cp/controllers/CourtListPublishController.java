package uk.gov.hmcts.cp.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.models.CourtListPayload;
import uk.gov.hmcts.cp.openapi.api.CourtListPublishApi;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishRequest;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishResponse;
import uk.gov.hmcts.cp.openapi.model.PublishStatus;
import uk.gov.hmcts.cp.services.CourtListPublishStatusService;
import uk.gov.hmcts.cp.services.CourtListQueryService;
import uk.gov.hmcts.cp.services.PdfGenerationTaskTriggerService;
import uk.gov.hmcts.cp.services.PublishTaskTriggerService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@RestController
public class CourtListPublishController implements CourtListPublishApi {

    private static final Logger LOG = LoggerFactory.getLogger(CourtListPublishController.class);

    private final CourtListPublishStatusService courtListPublishStatusService;
    private final PublishTaskTriggerService publishTaskTriggerService;
    private final CourtListQueryService courtListQueryService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String GENISIS_USER_ID = "7aee5dea-b0de-4604-b49b-86c7788cfc4b";

    @Autowired(required = false)
    private PdfGenerationTaskTriggerService pdfGenerationTaskTriggerService;

    public CourtListPublishController(final CourtListPublishStatusService courtListPublishStatusService,
                                      final PublishTaskTriggerService publishTaskTriggerService,
                                      final CourtListQueryService courtListQueryService) {
        this.courtListPublishStatusService = courtListPublishStatusService;
        this.publishTaskTriggerService = publishTaskTriggerService;
        this.courtListQueryService = courtListQueryService;
    }

    @Override
    public ResponseEntity<CourtListPublishResponse> publishCourtList(
            @RequestBody final CourtListPublishRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        final CourtListPublishResponse response = recordCourtListPublishStatus(request);

        CourtListPayload courtListResponse = queryProgressionForCourtListData(request);
        if (courtListResponse != null) {
            triggerCourtListPublisherTasks(courtListResponse, response.getCourtListId());
        } else {
            LOG.atWarn().log("Court list data query returned null for court list ID: {}. Skipping task triggers.",
                    response.getCourtListId());
        }

        return ResponseEntity.ok()
                .contentType(new MediaType("application", "vnd.courtlistpublishing-service.publish.post+json"))
                .body(response);
    }

    private CourtListPayload queryProgressionForCourtListData(final CourtListPublishRequest request) {
        try {

            String courtRoomId = request.getCourtRoomId() != null
                    ? request.getCourtRoomId().toString()
                    : null;

            String todayDate = LocalDate.now().format(DATE_FORMATTER);

            return courtListQueryService.queryCourtListResponse(
                    request.getCourtListType().toString(),
                    request.getCourtCentreId().toString(),
                    courtRoomId,
                    todayDate,
                    todayDate,
                    GENISIS_USER_ID
            );
        } catch (Exception e) {
            LOG.atWarn().log("Failed to query court list data: {}", e.getMessage());
            return null;
        }
    }

    private CourtListPublishResponse recordCourtListPublishStatus(final CourtListPublishRequest request) {
        final UUID courtListId = UUID.randomUUID();
        final PublishStatus publishStatus = PublishStatus.PUBLISH_REQUESTED;

        LOG.atInfo().log("Creating court list publish status record with ID: {} and status: {}", courtListId, publishStatus);

        return courtListPublishStatusService.createOrUpdate(
                courtListId,
                request.getCourtCentreId(),
                publishStatus,
                request.getCourtListType()
        );
    }

    private void triggerCourtListPublisherTasks(final CourtListPayload courtListResponse, final UUID courtListId) {
        triggerPublishingTask(courtListResponse, courtListId);
        triggerPdfGenerationTask(courtListResponse, courtListId);
    }

    private void triggerPublishingTask(final CourtListPayload courtListResponse, final UUID courtListId) {
        publishTaskTriggerService.triggerCourtListPublishingTask(courtListResponse, courtListId);
        LOG.atInfo().log("Court list publishing task triggered for court list ID: {}", courtListId);
    }

    private void triggerPdfGenerationTask(final CourtListPayload courtListResponse, final UUID courtListId) {
        if (pdfGenerationTaskTriggerService == null) {
            LOG.atDebug().log("PDF generation service is not enabled, skipping for court list ID: {}", courtListId);
            return;
        }

        pdfGenerationTaskTriggerService.triggerPdfGenerationTask(courtListResponse, courtListId);
        LOG.atInfo().log("PDF generation task triggered for court list ID: {}", courtListId);
    }

    @Override
    @SuppressWarnings("unused")
    public ResponseEntity<List<CourtListPublishResponse>> findCourtListPublishByCourtCenterId(
            @PathVariable final UUID courtCentreId) {
        LOG.atInfo().log("Fetching court list publish statuses for court centre ID: {}", courtCentreId);
        final List<CourtListPublishResponse> responses = courtListPublishStatusService.findByCourtCentreId(courtCentreId);
        return ResponseEntity.ok()
                .contentType(new MediaType("application", "vnd.courtlistpublishing-service.publish.get+json"))
                .body(responses);
    }
}

