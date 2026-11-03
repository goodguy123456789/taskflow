package com.taskflow.task.dto;

public record TaskInstanceReadyEvent(
        String eventId,
        String eventType,
        Long instanceId,
        Long definitionId,
        Long userId,
        String scheduledAt
) {
}