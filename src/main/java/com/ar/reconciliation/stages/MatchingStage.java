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
public class MatchingStage implements StageHandler {

    private final ObjectMapper objectMapper;
    private final FailureInjector failureInjector;

    @Override
    public StageType stageType() {
        return StageType.MATCHING;
    }

    @Override
    public JsonNode execute(WorkflowRun run) {
        failureInjector.maybeFail(StageType.MATCHING);

        JsonNode ingestionOutput = run.getStageOutputs().path(StageType.INGESTION.name());
        String invoiceId = ingestionOutput.path("invoiceId").asText();
        String paymentId = ingestionOutput.path("paymentId").asText();

        boolean idsMatch = invoiceId.regionMatches(true, 0, paymentId, 0,
                Math.min(3, Math.min(invoiceId.length(), paymentId.length())));
        double amount = ingestionOutput.path("amount").asDouble(0.0);

        double matchScore = idsMatch ? 0.9 : 0.4;
        if (amount > 0) matchScore += 0.05;

        ObjectNode output = objectMapper.createObjectNode();
        output.put("matchScore", matchScore);
        output.put("matchedInvoiceId", invoiceId);
        output.put("matchedPaymentId", paymentId);
        output.put("matchedAt", Instant.now().toString());

        log.info("Matching completed for workflow={} score={}", run.getId(), matchScore);
        return output;
    }
}
