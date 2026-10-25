package com.taskflow.task.dto;

public record CreateTaskResult(
        Long taskId,
        boolean created
) {}