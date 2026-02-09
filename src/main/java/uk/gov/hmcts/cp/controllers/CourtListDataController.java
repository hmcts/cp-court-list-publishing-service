package uk.gov.hmcts.cp.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.services.CourtListDataService;

/**
 * GET /courtlistdata – returns the same data as progression GET /courtlistdata.
 * Calls listing and reference data only (no progression).
 */
@RestController
@RequestMapping("/api/court-list-publish")
public class CourtListDataController {

    private static final Logger LOG = LoggerFactory.getLogger(CourtListDataController.class);
    private static final String PRISON = "PRISON";
    private static final String COURT_LIST_DATA_MEDIA_TYPE = "application/vnd.progression.search.court.list.data+json";

    private final CourtListDataService courtListDataService;

    public CourtListDataController(CourtListDataService courtListDataService) {
        this.courtListDataService = courtListDataService;
    }

    @GetMapping(value = "/courtlistdata", produces = {
        COURT_LIST_DATA_MEDIA_TYPE,
        "application/json"
    })
    public ResponseEntity<String> getCourtlistData(
            @RequestParam(name = "courtCentreId") String courtCentreId,
            @RequestParam(name = "courtRoomId", required = false) String courtRoomId,
            @RequestParam(name = "listId") CourtListType listId,
            @RequestParam(name = "startDate") String startDate,
            @RequestParam(name = "endDate") String endDate,
            @RequestParam(name = "restricted", defaultValue = "false") boolean restricted) {

        if (PRISON.equals(listId.name())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "PRISON listId is not supported for courtlistdata");
        }

        LOG.info("Fetching court list data (listing + reference data only) for listId: {}, courtCentreId: {}, startDate: {}, endDate: {}",
                listId, courtCentreId, startDate, endDate);

        String json = courtListDataService.getCourtListData(
                listId, courtCentreId, courtRoomId, startDate, endDate, restricted);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(COURT_LIST_DATA_MEDIA_TYPE))
                .body(json);
    }
}
