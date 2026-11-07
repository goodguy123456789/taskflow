package com.taskflow.task.dto;

public record TaskCreatedEvent(
        String eventId,
        String eventType,
        Long taskId,
        Long userId
) {}
