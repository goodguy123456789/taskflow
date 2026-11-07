package com.taskflow.task.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.task.entity.OutboxEvent;
import com.taskflow.task.entity.TaskDefinition;
import com.taskflow.task.entity.TaskInstance;
import com.taskflow.task.mapper.OutboxMapper;
import com.taskflow.task.mapper.TaskInstanceMapper;
import com.taskflow.task.mapper.TaskMapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SchedulerDispatchService {

    private final TaskMapper taskMapper;
    private final TaskInstanceMapper instanceMapper;
    private final OutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public int scheduleDueOnceBatch() {

        // 1. 查询并锁定最多 10 个到期任务定义
        List<TaskDefinition> definitions =
                taskMapper.selectDueOnceForUpdate(10);

        for (TaskDefinition definition : definitions) {

            // 2. 创建本次执行实例
            TaskInstance instance = new TaskInstance();

            instance.setDefinitionId(definition.getId());
            instance.setUserId(definition.getUserId());
            instance.setScheduledAt(
                    definition.getNextTriggerAt()
            );
            instance.setStatus("PENDING");
            instance.setHandlerType(
                    definition.getHandlerType()
            );
            instance.setPayload(definition.getPayload());
            instance.setMaxRetries(
                    definition.getMaxRetries()
            );

            int inserted = instanceMapper.insert(instance);

            if (inserted != 1 || instance.getId() == null) {
                throw new IllegalStateException(
                        "Failed to create task instance");
            }

            // 3. 创建 TASK_INSTANCE_READY 事件
            OutboxEvent event = new OutboxEvent();

            event.setEventId(UUID.randomUUID().toString());
            event.setAggregateType("TASK_INSTANCE");
            event.setAggregateId(instance.getId());
            event.setEventType("TASK_INSTANCE_READY");
            event.setStatus("NEW");

            try {
                String json = objectMapper.writeValueAsString(
                        Map.of(
                                "eventId", event.getEventId(),
                                "eventType", event.getEventType(),
                                "instanceId", instance.getId(),
                                "definitionId", definition.getId(),
                                "userId", definition.getUserId(),
                                "scheduledAt",
                                instance.getScheduledAt().toString()
                        )
                );

                event.setPayload(json);

            } catch (JsonProcessingException ex) {
                throw new IllegalStateException(
                        "Failed to serialize instance event",
                        ex);
            }

            int outboxInserted = outboxMapper.insert(event);

            if (outboxInserted != 1) {
                throw new IllegalStateException(
                        "Failed to save instance event");
            }

            // 4. 消除本次 ONCE 触发资格
            int updated = taskMapper.markOnceScheduled(
                    definition.getId(),
                    definition.getNextTriggerAt()
            );

            if (updated != 1) {
                throw new IllegalStateException(
                        "Failed to advance task schedule");
            }
        }

        return definitions.size();
    }
}
