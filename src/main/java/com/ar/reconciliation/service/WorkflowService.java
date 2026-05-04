package com.ar.reconciliation.service;

import com.ar.reconciliation.domain.IdempotencyKey;
import com.ar.reconciliation.domain.StageType;
import com.ar.reconciliation.domain.WorkflowRun;
import com.ar.reconciliation.domain.WorkflowState;
import com.ar.reconciliation.messaging.WorkflowPublisher;
import com.ar.reconciliation.repository.IdempotencyKeyRepository;
import com.ar.reconciliation.repository.WorkflowRunRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowService {

    private final WorkflowRunRepository runRepository;
    private final IdempotencyKeyRepository idempotencyRepository;
    private final WorkflowPublisher publisher;
    private final EventLogService eventLogService;
    private final ObjectMapper objectMapper;

    @Transactional
    public WorkflowRun submit(String idempotencyKey, JsonNode inputPayload) {
        var existing = runRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent replay for key={} → workflow={}",
                    idempotencyKey, existing.get().getId());
            return existing.get();
        }

        WorkflowRun run = WorkflowRun.builder()
                .idempotencyKey(idempotencyKey)
                .currentState(WorkflowState.PENDING)
                .inputPayload(inputPayload)
                .stageOutputs(objectMapper.createObjectNode())
                .retryCounts(objectMapper.createObjectNode())
                .globalRetryCount(0)
                .build();

        try {
            run = runRepository.save(run);
            idempotencyRepository.save(IdempotencyKey.builder()
                    .key(idempotencyKey)
                    .workflowId(run.getId())
                    .build());
        } catch (DataIntegrityViolationException ex) {
            log.warn("Race detected on idempotency key={}; refetching", idempotencyKey);
            return runRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> ex);
        }

        eventLogService.log(run.getId(), "WORKFLOW_CREATED",
                null, WorkflowState.PENDING, inputPayload, null);

        UUID workflowId = run.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publisher.publish(workflowId, StageType.INGESTION);
            }
        });
        log.info("Created and dispatched workflow={} key={}",
                run.getId(), idempotencyKey);
        return run;
    }

    @Transactional(readOnly = true)
    public WorkflowRun get(UUID id) {
        return runRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Workflow not found: " + id));
    }
}
