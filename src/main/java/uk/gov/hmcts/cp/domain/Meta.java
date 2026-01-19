package uk.gov.hmcts.cp.domain;

import lombok.Builder;
import lombok.Getter;

/**
 * Metadata for publishing hub publications.
 * 
 * <p>This class represents the metadata that accompanies a publication
 * to the Publishing Hub, including provenance, type, court information, etc.
 */
@Getter
@Builder
public class Meta {
    
    private final String provenance;
    private final String type;
    private final String listType;
    private final String courtId;
    private final String contentDate;
    private final String language;
    private final String sensitivity;
    private final String displayFrom;
    private final String displayTo;
}
