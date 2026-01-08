package uk.gov.hmcts.cp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.models.transformed.CourtListDocument;
import uk.gov.hmcts.cp.services.CourtListQueryService;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/court-list")
@RequiredArgsConstructor
@Slf4j
public class CourtListQueryController {

    private final CourtListQueryService courtListQueryService;

    @GetMapping(value = "/query", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CourtListDocument> queryCourtList(
            @RequestParam("listId") String listId,
            @RequestParam("courtCentreId") String courtCentreId,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestHeader(value = "CJSCPPUID", required = false) String cjscppuid) {

        log.info("Received court list query request: listId={}, courtCentreId={}, startDate={}, endDate={}, cjscppuid={}",
                listId, courtCentreId, startDate, endDate, cjscppuid);

        // Validate required parameters
        if (listId == null || listId.trim().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "listId parameter is required");
        }
        if (courtCentreId == null || courtCentreId.trim().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "courtCentreId parameter is required");
        }
        if (startDate == null || startDate.trim().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "startDate parameter is required");
        }
        if (endDate == null || endDate.trim().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "endDate parameter is required");
        }

        try {
            CourtListDocument document = courtListQueryService.queryCourtList(listId, courtCentreId, startDate, endDate, cjscppuid);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(document);
        } catch (Exception e) {
            log.error("Error processing court list query", e);
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to process court list query: " + e.getMessage()
            );
        }
    }
}

