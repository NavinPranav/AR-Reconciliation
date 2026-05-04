package com.ar.reconciliation.service;

import com.ar.reconciliation.domain.StageType;
import com.ar.reconciliation.domain.WorkflowRun;
import com.ar.reconciliation.domain.WorkflowState;
import com.ar.reconciliation.exception.PermanentStageException;
import com.ar.reconciliation.exception.TransientStageException;
import com.ar.reconciliation.messaging.WorkflowPublisher;
import com.ar.reconciliation.repository.WorkflowRunRepository;
import com.ar.reconciliation.stages.StageHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StageExecutionService {

    private final WorkflowRunRepository runRepository;
    private final EventLogService eventLogService;
    private final WorkflowPublisher publisher;
    private final ObjectMapper objectMapper;
    private final List<StageHandler> stageHandlers;

    @Value("${ar.retry.max-attempts-per-stage:5}")
    private int maxAttemptsPerStage;

    private Map<StageType, StageHandler> handlerByType;

    @PostConstruct
    void indexHandlers() {
        handlerByType = new EnumMap<>(StageType.class);
        for (StageHandler h : stageHandlers) {
            handlerByType.put(h.stageType(), h);
        }
    }

    @Transactional
    public StageOutcome processStage(UUID workflowId, StageType stage) {
        WorkflowRun run = runRepository.findByIdForUpdateSkipLocked(workflowId)
                .orElse(null);
        if (run == null) {
            log.warn("Workflow {} not found or locked by another consumer; skipping",
                    workflowId);
            return StageOutcome.SKIP;
        }

        if (!WorkflowState.validStartStatesFor(stage).contains(run.getCurrentState())) {
            log.info("Workflow {} in state {} is not eligible for stage {} (likely already processed) — acking",
                    workflowId, run.getCurrentState(), stage);
            return StageOutcome.ALREADY_DONE;
        }

        WorkflowState fromState = run.getCurrentState();
        run.setCurrentState(stage.inProgressState());
        runRepository.save(run);
        eventLogService.log(workflowId, "STAGE_STARTED",
                fromState, stage.inProgressState(), null, null);

        StageHandler handler = handlerByType.get(stage);
        JsonNode output;
        try {
            output = handler.execute(run);
        } catch (PermanentStageException pse) {
            log.error("Permanent failure in stage {} for workflow {}: {}",
                    stage, workflowId, pse.getMessage());
            markFailed(run, stage, pse.getMessage());
            return StageOutcome.PERMANENT_FAILURE;
        } catch (TransientStageException tse) {
            int attempts = incrementRetryCount(run, stage);
            run.setLastError(tse.getMessage());
            runRepository.save(run);
            eventLogService.log(workflowId, "STAGE_TRANSIENT_FAILURE",
                    stage.inProgressState(), stage.inProgressState(),
                    null, tse.getMessage());

            if (attempts >= maxAttemptsPerStage) {
                log.error("Max retries ({}) exhausted for stage {} on workflow {}",
                        maxAttemptsPerStage, stage, workflowId);
                markFailed(run, stage,
                        "Max retries exhausted: " + tse.getMessage());
                return StageOutcome.PERMANENT_FAILURE;
            }
            log.warn("Transient failure on stage {} workflow {} (attempt {}/{}): {}",
                    stage, workflowId, attempts, maxAttemptsPerStage, tse.getMessage());
            throw tse;
        }

        ObjectNode outputs = (ObjectNode) (run.getStageOutputs() != null
                ? run.getStageOutputs().deepCopy()
                : objectMapper.createObjectNode());
        outputs.set(stage.name(), output);
        run.setStageOutputs(outputs);
        run.setCurrentState(stage.completedState());
        run.setLastError(null);
        runRepository.save(run);

        eventLogService.log(workflowId, "STAGE_COMPLETED",
                stage.inProgressState(), stage.completedState(), output, null);

        StageType next = stage.completedState().nextStage();
        if (next != null) {
            StageType nextStage = next;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publisher.publish(workflowId, nextStage);
                }
            });
        } else {
            eventLogService.log(workflowId, "WORKFLOW_COMPLETED",
                    stage.completedState(), stage.completedState(), null, null);
            log.info("Workflow {} reached COMPLETED", workflowId);
        }

        return StageOutcome.SUCCESS;
    }

    private int incrementRetryCount(WorkflowRun run, StageType stage) {
        ObjectNode counts = (ObjectNode) (run.getRetryCounts() != null
                ? run.getRetryCounts().deepCopy()
                : objectMapper.createObjectNode());
        int current = counts.path(stage.name()).asInt(0) + 1;
        counts.put(stage.name(), current);
        run.setRetryCounts(counts);
        run.setGlobalRetryCount(run.getGlobalRetryCount() + 1);
        return current;
    }

    private void markFailed(WorkflowRun run, StageType stage, String error) {
        WorkflowState from = run.getCurrentState();
        run.setCurrentState(WorkflowState.FAILED);
        run.setLastError(error);
        runRepository.save(run);
        eventLogService.log(run.getId(), "WORKFLOW_FAILED",
                from, WorkflowState.FAILED, null, error);
    }

    public enum StageOutcome {
        SUCCESS,
        ALREADY_DONE,
        SKIP,
        PERMANENT_FAILURE
    }
}
