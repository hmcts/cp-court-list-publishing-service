package uk.gov.hmcts.cp.controllers.cleanup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /schedule-job.
 * Must include a valid cronExpression (e.g. "0 0/0 * * * ?").
 * Optional retentionDays: delete rows/blobs older than this many days (default 90).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CleanupJobRequest {

    @JsonProperty("cronExpression")
    private String cronExpression;

    @JsonProperty("retentionDays")
    private Integer retentionDays;
}
