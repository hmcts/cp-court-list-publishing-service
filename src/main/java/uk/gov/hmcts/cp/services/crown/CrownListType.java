package uk.gov.hmcts.cp.services.crown;

import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.services.PublicationSchema;

/**
 * The three Crown court list formats that CaTH accepts as Crown lists, each with the {@link CourtListType} values
 * that select it, its JSON schema, the root element of the document and the CaTH list type it is published as.
 */
public enum CrownListType {

    DAILY("CROWN_DAILY_LIST", "DailyList", "DL", PublicationSchema.CROWN_DAILY, false),
    ADVANCE("CROWN_ADVANCE_LIST", "WarnedList", "WL", PublicationSchema.CROWN_ADVANCE, true),
    FIRM("CROWN_FIRM_LIST", "FirmList", "FL", PublicationSchema.CROWN_FIRM, true);

    private final String cathListType;
    private final String rootElement;
    private final String documentType;
    private final PublicationSchema schema;
    private final boolean weekCommencing;

    CrownListType(final String cathListType, final String rootElement, final String documentType,
                 final PublicationSchema schema, final boolean weekCommencing) {
        this.cathListType = cathListType;
        this.rootElement = rootElement;
        this.documentType = documentType;
        this.schema = schema;
        this.weekCommencing = weekCommencing;
    }

    /**
     * DRAFT and FINAL select the daily list, WARN the advance list and FIRM the firm list.
     *
     * @throws IllegalArgumentException for any other list type
     */
    public static CrownListType from(final CourtListType courtListType) {
        if (courtListType == null) {
            throw new IllegalArgumentException("courtListType is required");
        }
        switch (courtListType) {
            case DRAFT:
            case FINAL:
                return DAILY;
            case WARN:
                return ADVANCE;
            case FIRM:
                return FIRM;
            default:
                throw new IllegalArgumentException("Unsupported court list type for a Crown list: " + courtListType
                        + " (supported: DRAFT, FINAL, WARN, FIRM)");
        }
    }

    public static boolean isSupported(final CourtListType courtListType) {
        return courtListType == CourtListType.DRAFT || courtListType == CourtListType.FINAL
                || courtListType == CourtListType.WARN || courtListType == CourtListType.FIRM;
    }

    /** The list type name CaTH knows this publication by (DtsMeta.listType). */
    public String cathListType() {
        return cathListType;
    }

    /** The JSON root element, as the Crown schema defines it. */
    public String rootElement() {
        return rootElement;
    }

    /** DocumentID.DocumentType, as Xhibit labels these lists. */
    public String documentType() {
        return documentType;
    }

    public PublicationSchema schema() {
        return schema;
    }

    /** Week-commencing lists (advance and firm) query the listing context by week, the daily list by day. */
    public boolean isWeekCommencing() {
        return weekCommencing;
    }
}
