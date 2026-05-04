package com.ar.reconciliation.repository;

import com.ar.reconciliation.domain.WorkflowRun;
import com.ar.reconciliation.domain.WorkflowState;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, UUID> {

    Optional<WorkflowRun> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
        @QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")
    })
    @Query("SELECT w FROM WorkflowRun w WHERE w.id = :id")
    Optional<WorkflowRun> findByIdForUpdateSkipLocked(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
        @QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")
    })
    @Query("""
            SELECT w FROM WorkflowRun w
             WHERE w.currentState IN :states
               AND w.updatedAt < :threshold
            ORDER BY w.updatedAt ASC
        """)
    List<WorkflowRun> findStuckWorkflows(
            @Param("states") List<WorkflowState> states,
            @Param("threshold") Instant threshold
    );
}
