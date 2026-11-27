package uk.gov.hmcts.cp.services;

public enum PublicationSchema {

    STANDARD("schema/standard-court-list-schema.json"),
    ONLINE_PUBLIC("schema/online-public-court-list-schema.json"),
    SJP_PUBLIC("schema/single-justice-procedure-public.json"),
    SJP_PRESS("schema/single-justice-procedure-press.json"),
    CROWN_DAILY_PDDA("schema/crown_daily_pdda_list.json"),
    CROWN_FIRM_PDDA("schema/crown_firm_pdda_list.json"),
    CROWN_ADVANCE_PDDA("schema/crown_advance_pdda_list.json");

    private final String path;

    PublicationSchema(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}
