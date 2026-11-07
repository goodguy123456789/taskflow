
package com.taskflow.task.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TaskDefinition {

    private Long id;
    private Long userId;

    private String name;
    private String description;

    private String handlerType;
    private String triggerType;
    private String cronExpression;
    private String timezone;
    private LocalDateTime nextTriggerAt;

    // 暂时用 String 承载数据库 JSON
    private String payload;

    private Integer maxRetries;
    private String status;

    private String idempotencyKey;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
