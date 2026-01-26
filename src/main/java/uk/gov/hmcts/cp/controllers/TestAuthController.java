package uk.gov.hmcts.cp.controllers;

import uk.gov.hmcts.cp.domain.DtsMeta;
import uk.gov.hmcts.cp.services.CaTHPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test controller for publishing functionality.
 *
 * <p>This controller provides test endpoints for the Publishing Hub integration.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class TestAuthController {

    private final CaTHPublisher publisher;

    /**
     * Test endpoint for publishing data to the Publishing Hub.
     *
     * <p>This endpoint accepts a payload and metadata, then calls the PublishingService
     * to send the data to the Publishing Hub V2 endpoint.
     *
     * <p>This endpoint is publicly accessible (no authentication required).
     *
     * <p>This endpoint consumes and produces only application/vnd.courtlistpublishing-service.test-auth.post+json
     *
     * @return the HTTP status code returned from the Publishing Hub
     */
    @PostMapping(value = "/test-auth",
            consumes = "application/vnd.courtlistpublishing-service.test-auth.post+json",
            produces = "application/vnd.courtlistpublishing-service.test-auth.post+json")
    public ResponseEntity<Integer> testAuth() {
        log.info("Test auth endpoint called with payload");
        // Fetch local token independently with try/catch
        final Integer res = publisher.publish(DUMMY_LIST, DUMMY_METADATA);
        return ResponseEntity.ok()
                .contentType(new MediaType("application", "vnd.courtlistpublishing-service.test-auth.post+json"))
                .body(res);
    }

    private static final String DUMMY_LIST = """
            {
              "document": {
                "info": {
                  "start_time": "09:00:00"
                },
                "data": {
                  "job": {
                    "printdate": "07/01/2026",
                    "sessions": {
                      "session": [
                        {
                          "lja": "Riverside",
                          "court": "Riverside Magistrates Court",
                          "room": 1,
                          "sstart": "09:30",
                          "blocks": {
                            "block": [
                              {
                                "bstart": "09:45",
                                "cases": {
                                  "case": [
                                    {
                                      "caseno": "9876543210",
                                      "def_name": "Morgan Smith"
                                    }
                                  ]
                                }
                              }
                            ]
                          }
                        }
                      ]
                    }
                  }
                }
              }
            }
            """;

    private static final DtsMeta DUMMY_METADATA = DtsMeta.builder()
            .provenance("COMMON_PLATFORM")
            .type("LIST")
            .listType("MAGISTRATES_PUBLIC_ADULT_COURT_LIST_DAILY")
            .courtId("0")// This should be court ID, a 3 digit string from refData
            .contentDate("2024-03-27T12:39:41.362Z")
            .language("ENGLISH")
            .sensitivity("PUBLIC")
            .displayFrom("2026-01-08T12:39:41.362Z")
            .displayTo("2026-01-11T13:39:41.362Z")
            .build();

}
