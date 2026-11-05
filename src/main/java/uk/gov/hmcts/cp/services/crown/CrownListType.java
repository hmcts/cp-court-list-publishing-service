package uk.gov.hmcts.cp.services.crown;

import uk.gov.hmcts.cp.openapi.model.CourtListType;
import uk.gov.hmcts.cp.services.PublicationSchema;

import java.util.Arrays;
import java.util.Optional;

/**
 * The crown court lists published to CaTH as PDDA lists (schemas in {@code hmcts/pip-data-management}), and the
 * Common Platform list each is built from.
 */
public enum CrownListType {

    DAILY(CourtListType.FINAL, "CROWN_DAILY_PDDA_LIST", "DailyList", "DL", PublicationSchema.CROWN_DAILY_PDDA, false),
    FIRM(CourtListType.FIRM, "CROWN_FIRM_PDDA_LIST", "FirmList", "FL", PublicationSchema.CROWN_FIRM_PDDA, true),
    WARNED(CourtListType.WARN, "CROWN_ADVANCE_PDDA_LIST", "WarnedList", "WL", PublicationSchema.CROWN_ADVANCE_PDDA, true);

    private final CourtListType courtListType;
    private final String cathListType;
    private final String rootElement;
    private final String documentType;
    private final PublicationSchema schema;
    private final boolean weekCommencing;

    CrownListType(CourtListType courtListType, String cathListType, String rootElement, String documentType,
                  PublicationSchema schema, boolean weekCommencing) {
        this.courtListType = courtListType;
        this.cathListType = cathListType;
        this.rootElement = rootElement;
        this.documentType = documentType;
        this.schema = schema;
        this.weekCommencing = weekCommencing;
    }

    /** The crown list for a Common Platform list type; empty when the type is not published as a crown list. */
    public static Optional<CrownListType> from(CourtListType courtListType) {
        return Arrays.stream(values()).filter(type -> type.courtListType == courtListType).findFirst();
    }

    public CourtListType courtListType() {
        return courtListType;
    }

    /** CaTH {@code x-list-type}. */
    public String cathListType() {
        return cathListType;
    }

    /** Top-level property of the CaTH document, e.g. {@code DailyList}. */
    public String rootElement() {
        return rootElement;
    }

    /** {@code DocumentID.DocumentType}. */
    public String documentType() {
        return documentType;
    }

    public PublicationSchema schema() {
        return schema;
    }

    /** True for the lists that cover a week commencing range rather than one day. */
    public boolean weekCommencing() {
        return weekCommencing;
    }
}
