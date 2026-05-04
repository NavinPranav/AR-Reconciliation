package com.ar.reconciliation.messaging.consumers;

import com.ar.reconciliation.domain.StageType;
import com.ar.reconciliation.messaging.WorkflowMessage;
import com.ar.reconciliation.service.StageExecutionService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class MatchingConsumer extends AbstractStageConsumer {

    public MatchingConsumer(StageExecutionService stageExecutionService) {
        super(stageExecutionService);
    }

    @Override
    protected StageType stageType() {
        return StageType.MATCHING;
    }

    @RabbitListener(
            queues = "ar.matching.q",
            concurrency = "${ar.consumers.matching.concurrency:5-20}"
    )
    public void onMessage(WorkflowMessage msg, Message rawMessage, Channel channel)
            throws IOException {
        handleMessage(msg, rawMessage, channel);
    }
}
