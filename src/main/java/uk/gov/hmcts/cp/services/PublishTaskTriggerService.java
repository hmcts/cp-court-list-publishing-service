package uk.gov.hmcts.cp.services;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.models.CourtListPayload;
import uk.gov.hmcts.cp.models.transformed.CourtListDocument;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus;
import uk.gov.hmcts.cp.taskmanager.domain.converter.JsonObjectConverter;
import uk.gov.hmcts.cp.taskmanager.service.ExecutionService;

import java.util.UUID;

import static java.time.ZonedDateTime.now;
import static reactor.netty.http.HttpConnectionLiveness.log;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo.executionInfo;


@Service
public class PublishTaskTriggerService {

    private static final Logger LOG = LoggerFactory.getLogger(PublishTaskTriggerService.class);
    private static final String TASK_NAME = "COURT_LIST_PUBLISH_TASK";

    @Autowired
    ExecutionService executionService;

    @Autowired
    JsonObjectConverter objectConverter;

    @Autowired
    PublicCourtListTransformationService publicCourtListTransformationService;

    @Autowired
    CourtListTransformationService transformationService;

    /**
     * Triggers the court list publishing task asynchronously.
     */
    public void triggerCourtListPublishingTask(final CourtListPayload courtListPayload,  final UUID courtListId) {
        if (courtListPayload == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Court list payload is required");
        }

        LOG.atInfo().log("Triggering court list publishing task for court list payload with listType: {}",
                courtListPayload.getListType());


        CourtListDocument document;
        if ("PUBLIC".equalsIgnoreCase(courtListPayload.getListType())) {
            log.info("Using PublicCourtListTransformationService for PUBLIC list type");
            document = publicCourtListTransformationService.transform(courtListPayload);
        } else {
            log.info("Using CourtListTransformationService for list type: {}", courtListPayload.getListType());
            document = transformationService.transform(courtListPayload);
        }

        JsonObject jobData = Json.createObjectBuilder()
                .add("courtListId", courtListId.toString())
                .add("payload", objectConverter.convertFromObject(document))
                .build();

        ExecutionInfo executionInfo = executionInfo()
                .withJobData(jobData)
                .withAssignedTaskName(TASK_NAME)
                .withAssignedTaskStartTime(now())
                .withExecutionStatus(ExecutionStatus.STARTED)
                .withShouldRetry(false)
                .build();

        executionService.executeWith(executionInfo);

        LOG.atInfo().log("Court list publishing task triggered successfully");
    }
}
