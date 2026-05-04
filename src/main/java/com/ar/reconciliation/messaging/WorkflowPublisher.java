package com.ar.reconciliation.messaging;

import com.ar.reconciliation.config.RabbitConfig;
import com.ar.reconciliation.domain.StageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(UUID workflowId, StageType targetStage) {
        WorkflowMessage msg = WorkflowMessage.of(workflowId, targetStage);
        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE,
                targetStage.routingKey(),
                msg
        );
        log.debug("Published workflow={} to stage={}", workflowId, targetStage);
    }
}
