package com.taskflow.task.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.task.entity.OutboxEvent;
import com.taskflow.task.entity.TaskInstance;
import com.taskflow.task.mapper.OutboxMapper;
import com.taskflow.task.mapper.TaskInstanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RetryDispatchService {
    private final TaskInstanceMapper instanceMapper;
    private final OutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public int dispatchDue(int delaySeconds, int limit) {
        if (delaySeconds < 0 || limit <= 0) {
            throw new IllegalArgumentException("Invalid retry delay or batch size");
        }
        List<TaskInstance> instances = instanceMapper.selectDueRetriesForUpdate(limit, delaySeconds);
        for (TaskInstance instance : instances) {
            if (instanceMapper.queueRetry(instance.getId(), instance.getAttemptCount()) != 1) {
                throw new IllegalStateException("Failed to queue retry: " + instance.getId());
            }
            OutboxEvent event = new OutboxEvent();
            event.setEventId(UUID.randomUUID().toString());
            event.setAggregateType("TASK_INSTANCE");
            event.setAggregateId(instance.getId());
            event.setEventType("TASK_INSTANCE_READY");
            try {
                event.setPayload(objectMapper.writeValueAsString(Map.of(
                        "eventId", event.getEventId(), "eventType", event.getEventType(),
                        "instanceId", instance.getId(), "definitionId", instance.getDefinitionId(),
                        "userId", instance.getUserId(), "scheduledAt", instance.getScheduledAt().toString())));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Failed to serialize retry event", ex);
            }
            if (outboxMapper.insert(event) != 1) {
                throw new IllegalStateException("Failed to save retry event: " + instance.getId());
            }
        }
        return instances.size();
    }
}
