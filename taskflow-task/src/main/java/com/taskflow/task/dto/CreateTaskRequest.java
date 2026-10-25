package com.taskflow.task.dto;

public record CreateTaskRequest(
        Long userId,
        String name
) {
}

