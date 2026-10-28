package uk.gov.hmcts.cp.services.crown;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Builds one Crown document from the listing context's court list payload.
 */
public interface CrownListTransformer {

    CrownListType type();

    /**
     * @return the whole document, with the schema's root element ({@code DailyList}, {@code WarnedList} or
     * {@code FirmList}) as its only property
     */
    ObjectNode transform(CrownTransformInput input);
}
