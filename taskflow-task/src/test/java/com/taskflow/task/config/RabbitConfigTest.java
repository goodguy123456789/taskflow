package com.taskflow.task.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class RabbitConfigTest {

    @Test
    void providesRabbitAdminForTopologyDeclaration() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.registerBean(ConnectionFactory.class,
                    () -> new CachingConnectionFactory());
            context.register(RabbitConfig.class);
            context.refresh();

            assertNotNull(context.getBean(RabbitAdmin.class));
        }
    }
}
