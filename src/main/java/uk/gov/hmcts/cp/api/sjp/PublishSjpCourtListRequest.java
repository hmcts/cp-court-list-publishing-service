package uk.gov.hmcts.cp.api.sjp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

/**
 * Request to publish an SJP (Single Justice Procedure) court list to CaTH.
 * Mirrors PubHub: listType (SJP_PUBLISH_LIST | SJP_PRESS_LIST), optional language, requestType, and listPayload.
 */
@Getter
public class PublishSjpCourtListRequest {

    public static final String SJP_PUBLISH_LIST = "SJP_PUBLISH_LIST";
    public static final String SJP_PRESS_LIST = "SJP_PRESS_LIST";

    @NotNull(message = "listType is required")
    private final String listType;

    private final String language;
    private final String requestType;
    private final SjpListPayload listPayload;

    @JsonCreator
    public PublishSjpCourtListRequest(
            @JsonProperty("listType") String listType,
            @JsonProperty("language") String language,
            @JsonProperty("requestType") String requestType,
            @JsonProperty("listPayload") SjpListPayload listPayload) {
        this.listType = listType;
        this.language = language;
        this.requestType = requestType;
        this.listPayload = listPayload;
    }

    public boolean isSjpPublishList() {
        return SJP_PUBLISH_LIST.equals(listType);
    }

    public boolean isSjpPressList() {
        return SJP_PRESS_LIST.equals(listType);
    }
}
