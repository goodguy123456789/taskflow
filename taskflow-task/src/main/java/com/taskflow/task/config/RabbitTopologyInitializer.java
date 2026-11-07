package com.taskflow.task.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitTopologyInitializer implements ApplicationRunner {

    private final AmqpAdmin amqpAdmin;
    private final DirectExchange taskExchange;
    private final Queue taskQueue;
    private final Binding taskBinding;

    @Override
    public void run(ApplicationArguments args) {
        try {
            amqpAdmin.declareExchange(taskExchange);
            amqpAdmin.declareQueue(taskQueue);
            amqpAdmin.declareBinding(taskBinding);
            log.info("RabbitMQ topology initialized");
        } catch (AmqpException ex) {
            log.warn("RabbitMQ is unavailable during startup; topology will be declared when the connection recovers", ex);
        }
    }
}
