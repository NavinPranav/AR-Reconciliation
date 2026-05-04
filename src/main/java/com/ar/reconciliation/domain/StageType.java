package com.ar.reconciliation.domain;

public enum StageType {
    INGESTION(WorkflowState.INGESTING, WorkflowState.INGESTED),
    MATCHING(WorkflowState.MATCHING, WorkflowState.MATCHED),
    VALIDATION(WorkflowState.VALIDATING, WorkflowState.VALIDATED),
    ROUTING(WorkflowState.ROUTING, WorkflowState.COMPLETED);

    private final WorkflowState inProgressState;
    private final WorkflowState completedState;

    StageType(WorkflowState inProgressState, WorkflowState completedState) {
        this.inProgressState = inProgressState;
        this.completedState = completedState;
    }

    public WorkflowState inProgressState() {
        return inProgressState;
    }

    public WorkflowState completedState() {
        return completedState;
    }

    public String queueName() {
        return "ar." + name().toLowerCase() + ".q";
    }

    public String dlqName() {
        return "ar." + name().toLowerCase() + ".dlq";
    }

    public String routingKey() {
        return "ar." + name().toLowerCase();
    }
}
