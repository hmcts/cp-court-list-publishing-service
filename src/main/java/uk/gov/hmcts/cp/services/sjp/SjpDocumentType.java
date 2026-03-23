package uk.gov.hmcts.cp.services.sjp;

/**
 * SJP document names for CaTH (aligned with staging PubHub DocumentType).
 */
public enum SjpDocumentType {

    SJP_PUBLIC_LIST("SJP Public list"),
    SJP_PRESS_LIST("SJP Press list");

    private final String value;

    SjpDocumentType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
