package uk.gov.hmcts.cp.services;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.models.CourtListPayload;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus;
import uk.gov.hmcts.cp.taskmanager.domain.converter.JsonObjectConverter;
import uk.gov.hmcts.cp.taskmanager.service.ExecutionService;

import java.util.UUID;

import static java.time.ZonedDateTime.now;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo.executionInfo;

@Service
@ConditionalOnProperty(name = "azure.storage.enabled", havingValue = "true", matchIfMissing = false)
public class PdfGenerationTaskTriggerService {

    private static final Logger LOG = LoggerFactory.getLogger(PdfGenerationTaskTriggerService.class);
    private static final String TASK_NAME = "PDF_GENERATION_TASK";

    @Autowired
    ExecutionService executionService;

    @Autowired
    JsonObjectConverter objectConverter;

    public void triggerPdfGenerationTask(final CourtListPayload payload, final UUID courtListId) {
        LOG.atInfo().log("Triggering PDF generation task for court list ID: {} and list type: {}",
                courtListId, payload.getListType());


        JsonObject payloadJson = objectConverter.convertFromObject(payload);
        JsonObject jobData = Json.createObjectBuilder()
                .add("courtListId", courtListId.toString())
                .add("payload", payloadJson)
                .build();

        ExecutionInfo executionInfo = executionInfo()
                .withJobData(jobData)
                .withAssignedTaskName(TASK_NAME)
                .withAssignedTaskStartTime(now())
                .withExecutionStatus(ExecutionStatus.STARTED)
                .withShouldRetry(false)
                .build();

        executionService.executeWith(executionInfo);

        LOG.atInfo().log("PDF generation task triggered successfully for court list ID: {}", courtListId);
    }
}
