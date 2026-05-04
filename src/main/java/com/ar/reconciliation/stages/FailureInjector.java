package com.ar.reconciliation.stages;

import com.ar.reconciliation.domain.StageType;
import com.ar.reconciliation.exception.TransientStageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Slf4j
public class FailureInjector {

    private final boolean enabled;
    private final Map<StageType, Double> rates;

    public FailureInjector(
            @Value("${ar.failure-injection.enabled:false}") boolean enabled,
            @Value("${ar.failure-injection.ingestion-rate:0.0}") double ingestionRate,
            @Value("${ar.failure-injection.matching-rate:0.0}") double matchingRate,
            @Value("${ar.failure-injection.validation-rate:0.0}") double validationRate,
            @Value("${ar.failure-injection.routing-rate:0.0}") double routingRate
    ) {
        this.enabled = enabled;
        this.rates = Map.of(
                StageType.INGESTION, ingestionRate,
                StageType.MATCHING, matchingRate,
                StageType.VALIDATION, validationRate,
                StageType.ROUTING, routingRate
        );
    }

    public void maybeFail(StageType stage) {
        if (!enabled) return;
        double rate = rates.getOrDefault(stage, 0.0);
        if (ThreadLocalRandom.current().nextDouble() < rate) {
            log.warn("Injected transient failure in stage={}", stage);
            throw new TransientStageException(
                    "Simulated transient failure in stage " + stage);
        }
    }
}
