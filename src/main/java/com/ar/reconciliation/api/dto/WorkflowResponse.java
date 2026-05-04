package com.ar.reconciliation.api.dto;

import com.ar.reconciliation.domain.WorkflowRun;
import com.ar.reconciliation.domain.WorkflowState;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record WorkflowResponse(
        UUID id,
        String idempotencyKey,
        WorkflowState currentState,
        JsonNode stageOutputs,
        JsonNode retryCounts,
        Integer globalRetryCount,
        String lastError,
        Instant createdAt,
        Instant updatedAt
) {
    public static WorkflowResponse from(WorkflowRun run) {
        return new WorkflowResponse(
                run.getId(),
                run.getIdempotencyKey(),
                run.getCurrentState(),
                run.getStageOutputs(),
                run.getRetryCounts(),
                run.getGlobalRetryCount(),
                run.getLastError(),
                run.getCreatedAt(),
                run.getUpdatedAt()
        );
    }
}
