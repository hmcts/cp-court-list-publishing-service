package uk.gov.hmcts.cp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.domain.Meta;
import uk.gov.hmcts.cp.services.PublishingService;

/**
 * Test controller for publishing functionality.
 * 
 * <p>This controller provides test endpoints for the Publishing Hub integration.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class TestAuthController {

    private final PublishingService publishingService;

    /**
     * Test endpoint for publishing data to the Publishing Hub.
     * 
     * <p>This endpoint accepts a payload and metadata, then calls the PublishingService
     * to send the data to the Publishing Hub V2 endpoint.
     * 
     * <p>This endpoint is publicly accessible (no authentication required).
     * 
     * <p>This endpoint consumes and produces only application/vnd.courtlistpublishing-service.test-auth.post
     * 
     * @param request the test publish request containing payload and metadata
     * @return the HTTP status code returned from the Publishing Hub
     */
    @PostMapping(value = "/test-auth", 
                 consumes = "application/vnd.courtlistpublishing-service.test-auth.post",
                 produces = "application/vnd.courtlistpublishing-service.test-auth.post")
    public ResponseEntity<Integer> testAuth(@RequestBody TestPublishRequest request) {
        log.info("Test auth endpoint called with payload length: {}", 
                request.getPayload() != null ? request.getPayload().length() : 0);
        
        Meta metadata = Meta.builder()
                .provenance(request.getProvenance())
                .type(request.getType())
                .listType(request.getListType())
                .courtId(request.getCourtId())
                .contentDate(request.getContentDate())
                .language(request.getLanguage())
                .sensitivity(request.getSensitivity())
                .displayFrom(request.getDisplayFrom())
                .displayTo(request.getDisplayTo())
                .build();
        
        Integer statusCode = publishingService.sendData(request.getPayload(), metadata);
        
        log.info("Test auth completed with status code: {}", statusCode);
        return ResponseEntity.ok()
                .contentType(new MediaType("application", "vnd.courtlistpublishing-service.test-auth.post"))
                .body(statusCode);
    }

    /**
     * Request DTO for test publish endpoint.
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TestPublishRequest {
        private String payload;
        private String provenance;
        private String type;
        private String listType;
        private String courtId;
        private String contentDate;
        private String language;
        private String sensitivity;
        private String displayFrom;
        private String displayTo;
    }
}
