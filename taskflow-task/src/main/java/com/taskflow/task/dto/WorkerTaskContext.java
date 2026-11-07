package com.taskflow.task.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkerTaskContext {

    private Long instanceId;
    private Long definitionId;

    private LocalDateTime scheduledAt;

    private Integer retryCount;
    private Integer maxRetries;
    private Integer attemptNo;

    private Long leaseVersion;
    private String workerId;

    private String handlerType;
    private String payload;
}