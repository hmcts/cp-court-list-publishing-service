package uk.gov.hmcts.cp.services;

public class SchemaValidationException extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public SchemaValidationException(String message) {
        super(message);
    }

    public SchemaValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
