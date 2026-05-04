package com.ar.reconciliation.stages;

import com.ar.reconciliation.domain.StageType;
import com.ar.reconciliation.domain.WorkflowRun;
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
public class RoutingStage implements StageHandler {

    private final ObjectMapper objectMapper;
    private final FailureInjector failureInjector;

    @Override
    public StageType stageType() {
        return StageType.ROUTING;
    }

    @Override
    public JsonNode execute(WorkflowRun run) {
        failureInjector.maybeFail(StageType.ROUTING);

        JsonNode validation = run.getStageOutputs().path(StageType.VALIDATION.name());
        boolean valid = validation.path("valid").asBoolean(false);
        JsonNode flags = validation.path("flags");

        String decision;
        String reason;
        if (!valid) {
            decision = "REJECT";
            reason = "Validation flags: " + flags;
        } else if (flags.size() > 0) {
            decision = "MANUAL_REVIEW";
            reason = "Soft flags require human review";
        } else {
            decision = "AUTO_APPROVE";
            reason = "All checks passed";
        }

        ObjectNode output = objectMapper.createObjectNode();
        output.put("decision", decision);
        output.put("reason", reason);
        output.put("decidedAt", Instant.now().toString());

        log.info("Routing completed for workflow={} decision={}", run.getId(), decision);
        return output;
    }
}
