package com.ar.reconciliation.messaging.consumers;

import com.ar.reconciliation.domain.StageType;
import com.ar.reconciliation.exception.PermanentStageException;
import com.ar.reconciliation.exception.TransientStageException;
import com.ar.reconciliation.messaging.WorkflowMessage;
import com.ar.reconciliation.service.StageExecutionService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;

import java.io.IOException;

@RequiredArgsConstructor
@Slf4j
public abstract class AbstractStageConsumer {

    private static final int MAX_ATTEMPTS = 5;
    private static final long INITIAL_BACKOFF_MS = 1000;
    private static final long MAX_BACKOFF_MS = 30_000;

    protected final StageExecutionService stageExecutionService;

    protected abstract StageType stageType();

    public void handleMessage(WorkflowMessage msg, Message rawMessage, Channel channel)
            throws IOException {
        long deliveryTag = rawMessage.getMessageProperties().getDeliveryTag();
        long backoff = INITIAL_BACKOFF_MS;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                log.debug("Processing workflow={} stage={} attempt={}/{}",
                        msg.workflowId(), stageType(), attempt, MAX_ATTEMPTS);
                stageExecutionService.processStage(msg.workflowId(), stageType());
                channel.basicAck(deliveryTag, false);
                return;
            } catch (PermanentStageException pse) {
                log.error("Permanent failure on stage {} workflow {} → DLQ",
                        stageType(), msg.workflowId(), pse);
                channel.basicNack(deliveryTag, false, false);
                return;
            } catch (TransientStageException tse) {
                if (attempt == MAX_ATTEMPTS) {
                    log.error("Exhausted {} retries for stage {} workflow {} → DLQ",
                            MAX_ATTEMPTS, stageType(), msg.workflowId(), tse);
                    channel.basicNack(deliveryTag, false, false);
                    return;
                }
                log.warn("Transient failure on stage {} workflow {} (attempt {}/{}), backing off {}ms: {}",
                        stageType(), msg.workflowId(), attempt, MAX_ATTEMPTS, backoff, tse.getMessage());
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    channel.basicNack(deliveryTag, false, false);
                    return;
                }
                backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
            }
        }
    }
}
