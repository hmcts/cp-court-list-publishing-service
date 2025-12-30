package uk.gov.hmcts.cp.controllers;

import uk.gov.hmcts.cp.dto.HearingRequest;
import uk.gov.hmcts.cp.services.HearingService;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController()
@RequestMapping(path = "api", produces = MediaType.TEXT_PLAIN_VALUE)
public class HearingController {

    public static final MediaType MEDIA_TYPE_GET = new MediaType("application", "vnd.courtlistpublishing-service.hearing.get+json");
    public static final MediaType MEDIA_TYPE_POST = new MediaType("application", "vnd.courtlistpublishing-service.hearing.post+json");
    private final HearingService hearingService;

    private static final Logger LOG = LoggerFactory.getLogger(HearingController.class);

    public HearingController(final HearingService hearingService) {
        this.hearingService = hearingService;
    }

    @GetMapping("/hearing/{hearingId}")
    public ResponseEntity<String> getHearingData(@PathVariable UUID hearingId) {
        final String hearing;
        if (hearingId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "HearingId is required");
        }

        hearing = hearingService.getHearingById(hearingId);
        return ResponseEntity.ok()
                .contentType(MEDIA_TYPE_GET)
                .body(hearing);
    }

    @PostMapping("/hearing")
    public ResponseEntity<String> postHearing(@RequestBody HearingRequest hearingRequest) {
        if (hearingRequest == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        String hearing = hearingService.updateHearing(hearingRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .contentType(MEDIA_TYPE_POST)
                .body(hearing);
    }

}
