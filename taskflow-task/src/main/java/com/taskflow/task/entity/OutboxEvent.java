
package com.taskflow.task.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OutboxEvent {

    private Long id;
    private String eventId;

    private String aggregateType;
    private Long aggregateId;

    private String eventType;
    private String payload;

    private String status;
    private Integer retryCount;

    private LocalDateTime nextRetryAt;
    private LocalDateTime createdAt;
}
