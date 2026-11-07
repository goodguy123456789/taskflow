
package com.taskflow.task.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TaskInstance {

    private Long id;
    private Long definitionId;
    private Long userId;

    private LocalDateTime scheduledAt;
    private String status;

    private String handlerType;
    private String payload;
    private Integer maxRetries;
    private Integer attemptCount;

    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private Long leaseVersion;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
