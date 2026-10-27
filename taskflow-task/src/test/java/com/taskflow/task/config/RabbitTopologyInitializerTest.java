package com.taskflow.task.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitTopologyInitializerTest {

    @Test
    void declaresRabbitTopologyDuringApplicationStartup() throws Exception {
        AmqpAdmin amqpAdmin = mock(AmqpAdmin.class);
        DirectExchange exchange = mock(DirectExchange.class);
        Queue queue = mock(Queue.class);
        Binding binding = mock(Binding.class);
        RabbitTopologyInitializer initializer =
                new RabbitTopologyInitializer(
                        amqpAdmin, exchange, queue, binding);

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(amqpAdmin).declareExchange(exchange);
        verify(amqpAdmin).declareQueue(queue);
        verify(amqpAdmin).declareBinding(binding);
    }
}
