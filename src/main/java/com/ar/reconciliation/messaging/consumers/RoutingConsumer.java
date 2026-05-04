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
public class RoutingConsumer extends AbstractStageConsumer {

    public RoutingConsumer(StageExecutionService stageExecutionService) {
        super(stageExecutionService);
    }

    @Override
    protected StageType stageType() {
        return StageType.ROUTING;
    }

    @RabbitListener(
            queues = "ar.routing.q",
            concurrency = "${ar.consumers.routing.concurrency:5-10}"
    )
    public void onMessage(WorkflowMessage msg, Message rawMessage, Channel channel)
            throws IOException {
        handleMessage(msg, rawMessage, channel);
    }
}
