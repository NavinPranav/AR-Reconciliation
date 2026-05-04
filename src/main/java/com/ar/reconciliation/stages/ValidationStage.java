package com.ar.reconciliation.stages;

import com.ar.reconciliation.domain.StageType;
import com.ar.reconciliation.domain.WorkflowRun;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class ValidationStage implements StageHandler {

    private final ObjectMapper objectMapper;
    private final FailureInjector failureInjector;

    @Override
    public StageType stageType() {
        return StageType.VALIDATION;
    }

    @Override
    public JsonNode execute(WorkflowRun run) {
        failureInjector.maybeFail(StageType.VALIDATION);

        JsonNode matchOutput = run.getStageOutputs().path(StageType.MATCHING.name());
        JsonNode ingestionOutput = run.getStageOutputs().path(StageType.INGESTION.name());

        double matchScore = matchOutput.path("matchScore").asDouble(0.0);
        double amount = ingestionOutput.path("amount").asDouble(0.0);

        ArrayNode flags = objectMapper.createArrayNode();
        if (matchScore < 0.5) flags.add("LOW_MATCH_SCORE");
        if (amount <= 0) flags.add("INVALID_AMOUNT");
        if (amount > 1_000_000) flags.add("HIGH_VALUE_REVIEW");

        boolean valid = flags.isEmpty() || (flags.size() == 1
                && flags.get(0).asText().equals("HIGH_VALUE_REVIEW"));

        ObjectNode output = objectMapper.createObjectNode();
        output.set("flags", flags);
        output.put("valid", valid);
        output.put("validatedAt", Instant.now().toString());

        log.info("Validation completed for workflow={} valid={} flags={}",
                run.getId(), valid, flags);
        return output;
    }
}
