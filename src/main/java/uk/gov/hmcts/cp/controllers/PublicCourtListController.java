package uk.gov.hmcts.cp.controllers;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import uk.gov.hmcts.cp.services.publiccourtlist.PublicCourtListException;
import uk.gov.hmcts.cp.services.publiccourtlist.PublicCourtListService;

@RestController
@RequestMapping("/api/public-court-list")
@ConditionalOnProperty(name = "public-court-list.enabled", havingValue = "true")
public class PublicCourtListController {

    private static final Logger LOG = LoggerFactory.getLogger(PublicCourtListController.class);
    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_PATTERN);
    private static final String PDF_FILENAME = "CourtList.pdf";
    private static final String CONTENT_DISPOSITION_VALUE = "attachment; filename=\"" + PDF_FILENAME + "\"";

    private final PublicCourtListService publicCourtListService;

    public PublicCourtListController(final PublicCourtListService publicCourtListService) {
        this.publicCourtListService = publicCourtListService;
    }

    @GetMapping(produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getPublicCourtListPdf(
            @RequestParam(name = "courtCentreId") final String courtCentreId,
            @RequestParam(name = "startDate") final String startDate,
            @RequestParam(name = "endDate") final String endDate) {

        if (courtCentreId == null || courtCentreId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "courtCentreId is required");
        }

        LocalDate start = parseDate("startDate", startDate);
        LocalDate end = parseDate("endDate", endDate);
        if (end.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate must be on or after startDate");
        }

        try {
            byte[] pdf = publicCourtListService.generatePublicCourtListPdf(courtCentreId, start, end);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, CONTENT_DISPOSITION_VALUE);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdf);
        } catch (PublicCourtListException e) {
            LOG.warn("Public court list error: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    private static LocalDate parseDate(final String paramName, final String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, paramName + " is required (format: " + DATE_PATTERN + ")");
        }
        try {
            return LocalDate.parse(value.trim(), DATE_FORMATTER);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, paramName + " must be in format " + DATE_PATTERN);
        }
    }
}
