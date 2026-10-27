package com.taskflow.task.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class RabbitConfigTest {

    @Test
    void providesRabbitAdminForTopologyDeclaration() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.registerBean(ConnectionFactory.class,
                    () -> mock(ConnectionFactory.class));
            context.register(RabbitConfig.class);
            context.refresh();

            assertNotNull(context.getBean(RabbitAdmin.class));
        }
    }
}
