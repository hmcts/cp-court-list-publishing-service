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

        try {
            hearing = hearingService.getHearingById(hearingId);
        } catch (Exception e) {
            LOG.atError().log(e.getMessage());
            throw e;
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(hearing);
    }

    @PostMapping("/hearing")
    public ResponseEntity<String> postHearing(@RequestBody HearingRequest hearingRequest) {
        if (hearingRequest == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        try {
            String hearing = hearingService.updateHearing(hearingRequest);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(hearing);
        } catch (IllegalArgumentException e) {
            LOG.atError().log("Invalid request: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IllegalStateException e) {
            LOG.atError().log("Conflict: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        } catch (Exception e) {
            LOG.atError().log("Error creating hearing: {}", e.getMessage(), e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to create hearing",
                    e
            );
        }
    }

}
