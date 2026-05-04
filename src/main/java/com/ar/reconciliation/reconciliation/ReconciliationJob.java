package com.ar.reconciliation.reconciliation;

import com.ar.reconciliation.domain.WorkflowRun;
import com.ar.reconciliation.domain.WorkflowState;
import com.ar.reconciliation.messaging.WorkflowPublisher;
import com.ar.reconciliation.repository.WorkflowRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(value = "ar.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class ReconciliationJob {

    private static final List<WorkflowState> STUCK_STATES = List.of(
            WorkflowState.PENDING,
            WorkflowState.INGESTING,
            WorkflowState.INGESTED,
            WorkflowState.MATCHING,
            WorkflowState.MATCHED,
            WorkflowState.VALIDATING,
            WorkflowState.VALIDATED,
            WorkflowState.ROUTING
    );

    private final WorkflowRunRepository runRepository;
    private final WorkflowPublisher publisher;

    @Value("${ar.reconciliation.stuck-threshold-minutes:10}")
    private int stuckThresholdMinutes;

    @Scheduled(fixedDelayString = "${ar.reconciliation.fixed-delay-ms:300000}",
               initialDelayString = "${ar.reconciliation.initial-delay-ms:60000}")
    @Transactional
    public void recoverStuckWorkflows() {
        Instant threshold = Instant.now().minus(Duration.ofMinutes(stuckThresholdMinutes));

        List<WorkflowRun> stuck = runRepository.findStuckWorkflows(STUCK_STATES, threshold);
        if (stuck.isEmpty()) {
            log.debug("Reconciliation: no stuck workflows");
            return;
        }

        log.warn("Reconciliation: {} stuck workflow(s) detected, republishing",
                stuck.size());
        for (WorkflowRun run : stuck) {
            var nextStage = run.getCurrentState().nextStage();
            if (nextStage == null) continue;
            publisher.publish(run.getId(), nextStage);
            log.info("Reconciliation republished workflow={} state={} → stage={}",
                    run.getId(), run.getCurrentState(), nextStage);
        }
    }
}
