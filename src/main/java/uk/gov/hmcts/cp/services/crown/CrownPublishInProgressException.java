package uk.gov.hmcts.cp.services.crown;

/** A crown list publish was requested while a publish of the same list is still in progress. */
public class CrownPublishInProgressException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CrownPublishInProgressException(String message, Throwable cause) {
        super(message, cause);
    }
}
