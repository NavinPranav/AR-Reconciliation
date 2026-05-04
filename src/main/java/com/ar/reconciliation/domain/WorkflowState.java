package com.ar.reconciliation.domain;

import java.util.Set;

public enum WorkflowState {
    PENDING,
    INGESTING,
    INGESTED,
    MATCHING,
    MATCHED,
    VALIDATING,
    VALIDATED,
    ROUTING,
    COMPLETED,
    FAILED;

    private static final Set<WorkflowState> TERMINAL = Set.of(COMPLETED, FAILED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public StageType nextStage() {
        return switch (this) {
            case PENDING, INGESTING -> StageType.INGESTION;
            case INGESTED, MATCHING -> StageType.MATCHING;
            case MATCHED, VALIDATING -> StageType.VALIDATION;
            case VALIDATED, ROUTING -> StageType.ROUTING;
            case COMPLETED, FAILED -> null;
        };
    }

    public static Set<WorkflowState> validStartStatesFor(StageType stage) {
        return switch (stage) {
            case INGESTION  -> Set.of(PENDING, INGESTING);
            case MATCHING   -> Set.of(INGESTED, MATCHING);
            case VALIDATION -> Set.of(MATCHED, VALIDATING);
            case ROUTING    -> Set.of(VALIDATED, ROUTING);
        };
    }
}
