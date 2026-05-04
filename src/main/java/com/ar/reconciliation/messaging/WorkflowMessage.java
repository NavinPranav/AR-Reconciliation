package com.ar.reconciliation.messaging;

import com.ar.reconciliation.domain.StageType;

import java.io.Serializable;
import java.util.UUID;

public record WorkflowMessage(
        UUID workflowId,
        StageType targetStage,
        long publishedAtEpochMs
) implements Serializable {

    public static WorkflowMessage of(UUID workflowId, StageType targetStage) {
        return new WorkflowMessage(workflowId, targetStage, System.currentTimeMillis());
    }
}
