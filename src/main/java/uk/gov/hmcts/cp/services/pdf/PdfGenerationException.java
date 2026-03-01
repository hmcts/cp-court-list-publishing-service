package uk.gov.hmcts.cp.services.pdf;

/**
 * Thrown when PDF generation fails (e.g. document generator API error or empty response).
 */
public class PdfGenerationException extends RuntimeException {

    public PdfGenerationException(String message) {
        super(message);
    }

    public PdfGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
