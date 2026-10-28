package uk.gov.hmcts.cp.services;

public enum PublicationSchema {

    STANDARD("schema/standard-court-list-schema.json"),
    ONLINE_PUBLIC("schema/online-public-court-list-schema.json"),
    SJP_PUBLIC("schema/single-justice-procedure-public.json"),
    SJP_PRESS("schema/single-justice-procedure-press.json"),
    /** HMCTS CaTH schemas, taken from pip-data-management (src/main/resources/schemas). */
    CROWN_DAILY("schema/crown_daily_list.json"),
    CROWN_ADVANCE("schema/crown_advance_list.json"),
    CROWN_FIRM("schema/crown_firm_list.json");

    private final String path;

    PublicationSchema(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}
