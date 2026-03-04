package uk.gov.hmcts.cp.services.courtlistdownload;

public class CourtListDownloadException extends RuntimeException {

    public CourtListDownloadException(String message) {
        super(message);
    }

    public CourtListDownloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
