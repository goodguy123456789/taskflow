package com.taskflow.task.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.DefaultApplicationArguments;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RabbitTopologyInitializerTest {

    @Test
    void declaresRabbitTopologyDuringApplicationStartup() throws Exception {
        List<String> declarations = new ArrayList<>();
        AmqpAdmin amqpAdmin = (AmqpAdmin) Proxy.newProxyInstance(
                AmqpAdmin.class.getClassLoader(),
                new Class<?>[]{AmqpAdmin.class},
                (proxy, method, args) -> {
                    declarations.add(method.getName());
                    return null;
                });
        DirectExchange exchange = new DirectExchange("test.events");
        Queue queue = new Queue("test.queue");
        Binding binding = BindingBuilder.bind(queue)
                .to(exchange)
                .with("test.routing-key");
        RabbitTopologyInitializer initializer =
                new RabbitTopologyInitializer(
                        amqpAdmin, exchange, queue, binding);

        initializer.run(new DefaultApplicationArguments(new String[0]));

        assertEquals(
                List.of("declareExchange", "declareQueue", "declareBinding"),
                declarations);
    }
}
