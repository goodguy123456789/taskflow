package com.taskflow.task.service;

import com.taskflow.task.dto.TaskCreatedEvent;
import com.taskflow.task.mapper.ConsumerMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskEventConsumerService {

    private static final String CONSUMER_NAME =
            "task-created-projection";

    private final ConsumerMapper consumerMapper;

    @Transactional
    public boolean process(TaskCreatedEvent event) {

        // 1. 验证收到的事件
        if (event == null
                || event.eventId() == null
                || event.eventId().isBlank()
                || event.eventId().length() > 64
                || event.taskId() == null
                || event.taskId() <= 0
                || event.userId() == null
                || event.userId() <= 0
                || !"TASK_DEFINITION_CREATED"
                        .equals(event.eventType())) {

            throw new IllegalArgumentException(
                    "Invalid task created event");
        }

        // 2. 尝试登记已处理事件
        int rows = consumerMapper.insertProcessedMessage(
                CONSUMER_NAME,
                event.eventId()
        );

        if (rows == 0) {
            // 相同消费者已经处理过这个事件
            return false;
        }

        if (rows != 1) {
            throw new IllegalStateException(
                    "Unexpected deduplication result");
        }

        // 3. 执行业务 SQL
        int businessRows = consumerMapper.insertProjection(
                event.taskId(),
                event.userId(),
                event.eventId()
        );

        if (businessRows != 1) {
            throw new IllegalStateException(
                    "Failed to create task projection");
        }

        return true;
    }
}
