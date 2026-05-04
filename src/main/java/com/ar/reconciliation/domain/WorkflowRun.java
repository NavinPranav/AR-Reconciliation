package com.ar.reconciliation.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "workflow_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowRun {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_state", nullable = false, length = 32)
    private WorkflowState currentState;

    @Type(JsonBinaryType.class)
    @Column(name = "input_payload", columnDefinition = "jsonb")
    private JsonNode inputPayload;

    @Type(JsonBinaryType.class)
    @Column(name = "stage_outputs", columnDefinition = "jsonb")
    private JsonNode stageOutputs;

    @Type(JsonBinaryType.class)
    @Column(name = "retry_counts", columnDefinition = "jsonb")
    private JsonNode retryCounts;

    @Column(name = "global_retry_count", nullable = false)
    private Integer globalRetryCount;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "locked_by", length = 64)
    private String lockedBy;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (currentState == null) currentState = WorkflowState.PENDING;
        if (globalRetryCount == null) globalRetryCount = 0;
    }
}
