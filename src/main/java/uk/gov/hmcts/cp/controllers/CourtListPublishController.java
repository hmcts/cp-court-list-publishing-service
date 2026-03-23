package uk.gov.hmcts.cp.controllers;

import org.springframework.beans.factory.annotation.Value;
import uk.gov.hmcts.cp.cleanup.CleanupJobService;
import uk.gov.hmcts.cp.config.AppConstant;
import uk.gov.hmcts.cp.openapi.api.CourtListPublishApi;
import uk.gov.hmcts.cp.openapi.model.CourtListDownloadRequest;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishRequest;
import uk.gov.hmcts.cp.openapi.model.CourtListPublishResponse;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.openapi.model.PublishStatusCleanupResponse;
import uk.gov.hmcts.cp.openapi.model.Status;
import uk.gov.hmcts.cp.services.courtlistdownload.CourtListDownloadException;
import uk.gov.hmcts.cp.services.courtlistdownload.CourtListDownloadService;
import uk.gov.hmcts.cp.services.CourtListPublishStatusService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.services.CourtListTaskTriggerService;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
public class CourtListPublishController implements CourtListPublishApi {

    private static final Logger LOG = LoggerFactory.getLogger(CourtListPublishController.class);

    private static final String PDF_FILENAME = "CourtList.pdf";
    private static final String CONTENT_DISPOSITION_VALUE = "attachment; filename=\"" + PDF_FILENAME + "\"";
    private static final String PUBLISH_STATUS_CLEANUP_MEDIA_TYPE = "application/vnd.courtlistpublishing-service.publish-status-cleanup.get+json";

    private final CourtListPublishStatusService service;
    private final CourtListTaskTriggerService courtListTaskTriggerService;
    private final CourtListDownloadService courtListDownloadService;
    private final CleanupJobService cleanupJobService;

    @Value("${cleanup.publish-status-cleanup-days:90}")
    private int publishStatusCleanupDays;

    public CourtListPublishController(final CourtListPublishStatusService service,
                                     CourtListTaskTriggerService courtListTaskTriggerService,
                                     CourtListDownloadService courtListDownloadService,
                                     CleanupJobService cleanupJobService) {
        this.service = service;
        this.courtListTaskTriggerService = courtListTaskTriggerService;
        this.courtListDownloadService = courtListDownloadService;
        this.cleanupJobService = cleanupJobService;
    }


    @Override
    public ResponseEntity<CourtListPublishResponse> publishCourtList(
            @RequestBody final CourtListPublishRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        LOG.atInfo().log("Creating or updating court list publish status for court centre ID: {}, type: {}, startDate: {}, endDate: {}",
                request.getCourtCentreId(), request.getCourtListType(), request.getStartDate(), request.getEndDate());

        final CourtListPublishResponse response = service.createOrUpdate(
                request.getCourtCentreId(),
                request.getCourtListType(),
                request.getStartDate(),
                request.getEndDate()
        );

        String userId = getCjscppuidFromRequest();
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CJSCPPUID header is required");
        }

        // Trigger the court list publishing and PDF generation task asynchronously (userId from CJSCPPUID header)
        try {
            courtListTaskTriggerService.triggerCourtListTask(response, userId);
            LOG.atInfo().log("Court list publishing task triggered for court list ID: {}", response.getCourtListId());
        } catch (Exception e) {
            LOG.atError().log("Failed to trigger court list publishing task for court list ID: {}",
                    response.getCourtListId(), e);
        }

        return ResponseEntity.ok()
                .contentType(new MediaType("application", "vnd.courtlistpublishing-service.publish.post+json"))
                .body(response);
    }

    @Override
    public ResponseEntity<Resource> downloadCourtList(@RequestBody CourtListDownloadRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (request.getCourtCentreId() == null || request.getCourtCentreId().toString().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "courtCentreId is required");
        }
        if (request.getStartDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate is required (format: yyyy-MM-dd)");
        }
        if (request.getEndDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate is required (format: yyyy-MM-dd)");
        }
        if (request.getCourtListType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "courtListType is required");
        }
        if (!CourtListType.PUBLIC.equals(request.getCourtListType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Only PUBLIC court list type is supported for download. Got: " + request.getCourtListType());
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate must be on or after startDate");
        }
        try {
            byte[] pdf = courtListDownloadService.generatePublicCourtListPdf(
                request.getCourtCentreId().toString(),
                request.getStartDate(),
                request.getEndDate());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, CONTENT_DISPOSITION_VALUE);
            return ResponseEntity.ok().headers(headers).body(new ByteArrayResource(pdf));
        } catch (CourtListDownloadException e) {
            LOG.warn("Public court list download error: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    @SuppressWarnings("unused") // Method is used by Spring's request mapping
    public ResponseEntity<List<CourtListPublishResponse>> findCourtListPublishStatus(
            @RequestParam(required = false) final UUID courtListId,
            @RequestParam(required = false) final UUID courtCentreId,
            @RequestParam(required = false) final LocalDate publishDate,
            @RequestParam(required = false) final CourtListType courtListType) {
        LOG.atInfo().log("Fetching court list publish statuses - courtListId: {}, courtCentreId: {}, publishDate: {}, courtListType: {}",
                courtListId, courtCentreId, publishDate, courtListType);
        final List<CourtListPublishResponse> responses = service.findPublishStatus(
                courtListId, courtCentreId, publishDate, courtListType);
        return ResponseEntity.ok()
                .contentType(new MediaType("application", "vnd.courtlistpublishing-service.publish.get+json"))
                .body(responses);
    }

    @Override
    public ResponseEntity<PublishStatusCleanupResponse> publishStatusCleanup() {
        try {
            cleanupJobService.cleanupOldData(publishStatusCleanupDays);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(PUBLISH_STATUS_CLEANUP_MEDIA_TYPE))
                    .body(PublishStatusCleanupResponse.builder().success(true).build());
        } catch (Exception e) {
            LOG.error("Publish status cleanup failed", e);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(PUBLISH_STATUS_CLEANUP_MEDIA_TYPE))
                    .body(PublishStatusCleanupResponse.builder().success(false).build());
        }
    }

    private static String getCjscppuidFromRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest().getHeader(AppConstant.CJSCPPUID);
        }
        return null;
    }
}

