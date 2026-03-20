package uk.gov.hmcts.cp.exception;

public class BadDataException extends RuntimeException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public BadDataException(String message) {
        super(message);
    }

    public BadDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
