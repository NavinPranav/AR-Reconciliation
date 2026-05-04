package com.ar.reconciliation.stages;

import com.ar.reconciliation.domain.StageType;
import com.ar.reconciliation.domain.WorkflowRun;
import com.fasterxml.jackson.databind.JsonNode;

public interface StageHandler {
    StageType stageType();

    JsonNode execute(WorkflowRun run);
}
