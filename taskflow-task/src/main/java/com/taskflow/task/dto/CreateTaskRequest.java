
package com.taskflow.task.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;

public record CreateTaskRequest(
        Long userId,
        String name,
        String description,
        String handlerType,
        String triggerType,
        String cronExpression,
        String timezone,
        LocalDateTime nextTriggerAt,
        JsonNode payload,
        Integer maxRetries,
        String idempotencyKey
) {}
