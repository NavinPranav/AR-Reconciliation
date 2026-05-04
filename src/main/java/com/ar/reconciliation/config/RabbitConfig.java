package com.ar.reconciliation.config;

import com.ar.reconciliation.domain.StageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "ar.workflow";
    public static final String DLX_EXCHANGE = "ar.workflow.dlx";

    @Bean
    public TopicExchange workflowExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public TopicExchange dlxExchange() {
        return ExchangeBuilder.topicExchange(DLX_EXCHANGE).durable(true).build();
    }

    @Bean
    public Declarables stageQueues() {
        var declarables = new Declarables();
        for (StageType stage : StageType.values()) {
            Map<String, Object> args = new HashMap<>();
            args.put("x-dead-letter-exchange", DLX_EXCHANGE);
            args.put("x-dead-letter-routing-key", stage.routingKey() + ".dlq");

            Queue mainQueue = new Queue(stage.queueName(), true, false, false, args);
            Queue dlq = new Queue(stage.dlqName(), true, false, false);

            Binding mainBinding = BindingBuilder
                    .bind(mainQueue)
                    .to(workflowExchange())
                    .with(stage.routingKey());

            Binding dlqBinding = BindingBuilder
                    .bind(dlq)
                    .to(dlxExchange())
                    .with(stage.routingKey() + ".dlq");

            declarables.getDeclarables().add(mainQueue);
            declarables.getDeclarables().add(dlq);
            declarables.getDeclarables().add(mainBinding);
            declarables.getDeclarables().add(dlqBinding);
        }
        return declarables;
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(cf);
        template.setMessageConverter(converter);
        template.setMandatory(true);
        return template;
    }
}
