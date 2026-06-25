package uk.gov.hmcts.cp.services.sjp;

/**
 * SJP document names for CaTH (aligned with staging PubHub DocumentType).
 * SJP_PUBLIC_LIST handles public.sjp.pending-cases-public-list-generated.
 * SJP_PRESS_LIST handles public.sjp.pending-cases-press-list-generated.
 * The press transparency report (public.sjp.press-transparency-report-generated) is out of scope.
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
