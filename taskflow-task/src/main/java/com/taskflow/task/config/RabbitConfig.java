
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

    public static final String DLX = "task.dlx";
    public static final String DLQ =
            "task.definition.created.dlq";
    public static final String DEAD_ROUTING_KEY =
            "task.definition.created.failed";

    @Bean
    @ConditionalOnMissingBean(AmqpAdmin.class)
    public RabbitAdmin taskflowRabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    // 原有交换机
    @Bean
    public DirectExchange taskExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    // 原有队列：保持原样，不增加 x-arguments
    @Bean
    public Queue taskQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    // 原有绑定
    @Bean
    public Binding taskBinding(
            DirectExchange taskExchange,
            Queue taskQueue) {

        return BindingBuilder
                .bind(taskQueue)
                .to(taskExchange)
                .with(ROUTING_KEY);
    }

    // 新增：死信交换机
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    // 新增：死信队列
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    // 新增：死信绑定
    @Bean
    public Binding deadLetterBinding(
            DirectExchange deadLetterExchange,
            Queue deadLetterQueue) {

        return BindingBuilder
                .bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(DEAD_ROUTING_KEY);
    }
}
