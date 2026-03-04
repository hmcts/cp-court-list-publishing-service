package uk.gov.hmcts.cp.controllers;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.config.AppConstant;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;
import uk.gov.hmcts.cp.openapi.api.CourtListPublishApi;
import uk.gov.hmcts.cp.openapi.model.*;
import uk.gov.hmcts.cp.services.CourtListDataService;
import uk.gov.hmcts.cp.services.CourtListPublishStatusService;
import uk.gov.hmcts.cp.services.CourtListTaskTriggerService;
import uk.gov.hmcts.cp.services.courtlistdownload.CourtListDownloadException;
import uk.gov.hmcts.cp.services.courtlistdownload.CourtListDownloadService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
public class CourtListPublishController implements CourtListPublishApi {

    private static final Logger LOG = LoggerFactory.getLogger(CourtListPublishController.class);
    private static final String PRISON = "PRISON";

    private static final String PDF_FILENAME = "CourtList.pdf";
    private static final String CONTENT_DISPOSITION_VALUE = "attachment; filename=\"" + PDF_FILENAME + "\"";

    private final CourtListPublishStatusService service;
    private final CourtListTaskTriggerService courtListTaskTriggerService;
    private final CourtListDataService courtListDataService;
    private final CourtListDownloadService courtListDownloadService;

    public CourtListPublishController(final CourtListPublishStatusService service,
                                      CourtListTaskTriggerService courtListTaskTriggerService,
                                      CourtListDataService courtListDataService,
                                      CourtListDownloadService courtListDownloadService) {
        this.service = service;
        this.courtListTaskTriggerService = courtListTaskTriggerService;
        this.courtListDataService = courtListDataService;
        this.courtListDownloadService = courtListDownloadService;
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
    public ResponseEntity<CourtListData> getCourtlistData(final UUID courtCentreId,
                                                          final CourtListType listId,
                                                          final LocalDate startDate,
                                                          final LocalDate endDate,
                                                          final UUID courtListId,
                                                          final Boolean restricted) {
        if (PRISON.equals(listId.name())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "PRISON listId is not supported for courtlistdata");
        }
        String cjscppuid = getCjscppuidFromRequest();
        if (cjscppuid == null || cjscppuid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CJSCPPUID header is required");
        }
        LOG.info("Fetching court list data from progression for listId: {}, courtCentreId: {}, startDate: {}, endDate: {} and user ID {}",
                listId, courtCentreId, startDate, endDate, cjscppuid);
        String courtCentreIdStr = courtCentreId != null ? courtCentreId.toString() : null;
        String startStr = startDate != null ? startDate.toString() : null;
        String endStr = endDate != null ? endDate.toString() : null;
        boolean rest = Boolean.TRUE.equals(restricted);
        String json = courtListDataService.getCourtListData(listId, courtCentreIdStr, null, startStr, endStr, rest,
                cjscppuid);
        try {
            CourtListData data = ObjectMapperConfig.getObjectMapper().readValue(json, CourtListData.class);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            logUnknownPropertiesFromDeserializationError(e, CourtListData.class);
            LOG.warn("Could not parse court list data as CourtListData, returning error: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to parse court list data");
        }
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

    /**
     * Logs any unknown JSON properties that caused deserialization to fail, so they can be added to the model.
     * Walks the exception cause chain and logs each {@link UnrecognizedPropertyException}.
     */
    private void logUnknownPropertiesFromDeserializationError(Throwable e, Class<?> targetType) {
        List<String> unknownProperties = new ArrayList<>();
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof UnrecognizedPropertyException upe) {
                String prop = upe.getPropertyName();
                if (prop != null && !unknownProperties.contains(prop)) {
                    unknownProperties.add(prop);
                }
            }
            if (t instanceof JsonMappingException jme && jme.getPath() != null && !jme.getPath().isEmpty()) {
                var ref = jme.getPath().get(jme.getPath().size() - 1);
                if (ref != null && ref.getFieldName() != null && !unknownProperties.contains(ref.getFieldName())) {
                    unknownProperties.add(ref.getFieldName());
                }
            }
        }
        if (!unknownProperties.isEmpty()) {
            LOG.warn("Court list data JSON contains unknown properties for {}: {}. Add these to the OpenAPI model to fix deserialization.",
                    targetType.getSimpleName(), unknownProperties);
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

