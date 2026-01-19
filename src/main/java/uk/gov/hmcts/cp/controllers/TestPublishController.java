package uk.gov.hmcts.cp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class TestPublishController {

    private final PublishingService publishingService;

    /**
     * Test endpoint for publishing data to the Publishing Hub.
     * 
     * <p>This endpoint accepts a payload and metadata, then calls the PublishingService
     * to send the data to the Publishing Hub V2 endpoint.
     * 
     * @param request the test publish request containing payload and metadata
     * @return the HTTP status code returned from the Publishing Hub
     */
    @PostMapping("/test-publish")
    public ResponseEntity<Integer> testPublish(@RequestBody TestPublishRequest request) {
        log.info("Test publish endpoint called with payload length: {}", 
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
        
        log.info("Test publish completed with status code: {}", statusCode);
        return ResponseEntity.ok(statusCode);
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
