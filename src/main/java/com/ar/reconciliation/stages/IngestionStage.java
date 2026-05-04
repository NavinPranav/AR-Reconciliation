package com.ar.reconciliation.stages;

import com.ar.reconciliation.domain.StageType;
import com.ar.reconciliation.domain.WorkflowRun;
import com.ar.reconciliation.exception.PermanentStageException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class IngestionStage implements StageHandler {

    private final ObjectMapper objectMapper;
    private final FailureInjector failureInjector;

    @Override
    public StageType stageType() {
        return StageType.INGESTION;
    }

    @Override
    public JsonNode execute(WorkflowRun run) {
        failureInjector.maybeFail(StageType.INGESTION);

        JsonNode input = run.getInputPayload();
        if (input == null || !input.has("invoiceId") || !input.has("paymentId")) {
            throw new PermanentStageException(
                    "Missing required fields invoiceId/paymentId in input payload");
        }

        ObjectNode output = objectMapper.createObjectNode();
        output.put("invoiceId", input.path("invoiceId").asText());
        output.put("paymentId", input.path("paymentId").asText());
        output.put("amount", input.path("amount").asDouble(0.0));
        output.put("currency", input.path("currency").asText("USD"));
        output.put("normalizedAt", Instant.now().toString());

        log.info("Ingestion completed for workflow={}", run.getId());
        return output;
    }
}
