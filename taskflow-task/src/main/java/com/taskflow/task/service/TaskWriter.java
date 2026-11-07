
package com.taskflow.task.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.task.entity.OutboxEvent;
import com.taskflow.task.entity.TaskDefinition;
import com.taskflow.task.mapper.OutboxMapper;
import com.taskflow.task.mapper.TaskMapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskWriter {

    private final TaskMapper taskMapper;
    private final OutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public Long insertTask(TaskDefinition task) {

        // 第一步：保存任务定义
        int rows = taskMapper.insert(task);

        if (rows != 1 || task.getId() == null) {
            throw new IllegalStateException(
                    "Failed to insert task definition");
        }

        // 第二步：构造任务创建事件
        OutboxEvent event = new OutboxEvent();

        event.setEventId(UUID.randomUUID().toString());
        event.setAggregateType("TASK_DEFINITION");
        event.setAggregateId(task.getId());
        event.setEventType("TASK_DEFINITION_CREATED");
        event.setStatus("PENDING");

        try {
            String json = objectMapper.writeValueAsString(
                    Map.of(
                            "eventId", event.getEventId(),
                            "eventType", event.getEventType(),
                            "taskId", task.getId(),
                            "userId", task.getUserId()
                    )
            );

            event.setPayload(json);

        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Failed to serialize task event", ex);
        }

        // 第三步：保存 Outbox 事件
        int outboxRows = outboxMapper.insert(event);

        if (outboxRows != 1) {
            throw new IllegalStateException(
                    "Failed to insert outbox event");
        }

        // 返回新任务 ID
        return task.getId();
    }
}
