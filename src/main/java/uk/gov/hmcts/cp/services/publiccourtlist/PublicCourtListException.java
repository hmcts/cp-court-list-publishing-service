package uk.gov.hmcts.cp.services.publiccourtlist;

public class PublicCourtListException extends RuntimeException {

    public PublicCourtListException(String message) {
        super(message);
    }

    public PublicCourtListException(String message, Throwable cause) {
        super(message, cause);
    }
}
