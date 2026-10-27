
package com.taskflow.task.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "task.events";
    public static final String ROUTING_KEY =
            "task.definition.created";
    public static final String QUEUE =
            "task.definition.created.q";

    @Bean
    @ConditionalOnMissingBean(AmqpAdmin.class)
    public RabbitAdmin taskflowRabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public DirectExchange taskExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue taskQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    public Binding taskBinding(
            DirectExchange taskExchange,
            Queue taskQueue) {

        return BindingBuilder
                .bind(taskQueue)
                .to(taskExchange)
                .with(ROUTING_KEY);
    }
}
