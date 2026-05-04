package com.ar.reconciliation.repository;

import com.ar.reconciliation.domain.WorkflowEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkflowEventRepository extends JpaRepository<WorkflowEvent, Long> {
    List<WorkflowEvent> findByWorkflowIdOrderByOccurredAtAsc(UUID workflowId);
}
