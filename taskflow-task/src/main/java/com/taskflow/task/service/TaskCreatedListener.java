package com.taskflow.task.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.task.config.RabbitConfig;
import com.taskflow.task.dto.TaskCreatedEvent;
import com.taskflow.task.service.TaskEventConsumerService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskCreatedListener {

    private final ObjectMapper objectMapper;
    private final TaskEventConsumerService consumerService;

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void handle(String json) throws Exception {

        // 1. JSON → Java record
        TaskCreatedEvent event =
                objectMapper.readValue(
                        json,
                        TaskCreatedEvent.class
                );

        // 2. 调用另一个 Spring Bean 的事务方法
        boolean firstProcessed =
                consumerService.process(event);

        // 3. 记录消费结果
        if (firstProcessed) {
            log.info(
                    "Task event processed: {}",
                    event.eventId()
            );
        } else {
            log.info(
                    "Duplicate task event ignored: {}",
                    event.eventId()
            );
        }
    }
}
