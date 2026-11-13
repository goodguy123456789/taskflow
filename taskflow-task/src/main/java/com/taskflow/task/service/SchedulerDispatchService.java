package com.taskflow.task.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.task.entity.OutboxEvent;
import com.taskflow.task.entity.TaskDefinition;
import com.taskflow.task.entity.TaskInstance;
import com.taskflow.task.mapper.OutboxMapper;
import com.taskflow.task.mapper.TaskInstanceMapper;
import com.taskflow.task.mapper.TaskMapper;
import com.taskflow.task.scheduler.CronTriggerCalculator;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SchedulerDispatchService {

    private final TaskMapper taskMapper;
    private final TaskInstanceMapper instanceMapper;
    private final OutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    private long cronMisfireGraceSeconds;

    @Value("${taskflow.scheduler.cron-misfire-grace-seconds:300}")
    void setCronMisfireGraceSeconds(long seconds) {
        if (seconds < 0) {
            throw new IllegalArgumentException("cron-misfire-grace-seconds must not be negative");
        }
        this.cronMisfireGraceSeconds = seconds;
    }

    @Transactional
    public int scheduleDueBatch() {

        // 1. 查询并锁定最多 10 个到期任务定义
        List<TaskDefinition> definitions =
                taskMapper.selectDueForUpdate(10);

        if (definitions.isEmpty()) {
            return 0;
        }

        // 和到期查询用同一个数据库时钟，每轮只计算一次补偿窗口。
        LocalDateTime cutoff = taskMapper.selectUtcNow().minusSeconds(cronMisfireGraceSeconds);
        int dispatched = 0;
        for (TaskDefinition definition : definitions) {
            LocalDateTime scheduledAt = definition.getNextTriggerAt();
            boolean cron = "CRON".equals(definition.getTriggerType());
            ZoneId taskZone = cron ? ZoneId.of(definition.getTimezone()) : null;

            if (cron && scheduledAt.isBefore(cutoff)) {
                // 跳到窗口内第一个匹配点。nextUtcAfter 是严格大于，所以包含窗口边界。
                LocalDateTime firstRetained = CronTriggerCalculator.nextUtcAfter(
                        definition.getCronExpression(), cutoff.minusNanos(1), taskZone);
                advanceSchedule(definition, scheduledAt, firstRetained);
                // 这轮只跳过过旧触发点。保留的触发点由后续扫描处理。
                continue;
            }

            LocalDateTime nextTriggerAt = cron
                    ? CronTriggerCalculator.nextUtcAfter(
                            definition.getCronExpression(), scheduledAt, taskZone)
                    : null;

            // 2. 创建本次执行实例
            TaskInstance instance = new TaskInstance();

            instance.setDefinitionId(definition.getId());
            instance.setUserId(definition.getUserId());
            instance.setScheduledAt(scheduledAt);
            instance.setStatus("PENDING");
            instance.setHandlerType(
                    definition.getHandlerType()
            );
            instance.setPayload(definition.getPayload());
            instance.setMaxRetries(
                    definition.getMaxRetries()
            );

            int inserted = instanceMapper.insert(instance);

            if (inserted != 1 || instance.getId() == null) {
                throw new IllegalStateException(
                        "Failed to create task instance");
            }

            // 3. 创建 TASK_INSTANCE_READY 事件
            OutboxEvent event = new OutboxEvent();

            event.setEventId(UUID.randomUUID().toString());
            event.setAggregateType("TASK_INSTANCE");
            event.setAggregateId(instance.getId());
            event.setEventType("TASK_INSTANCE_READY");
            event.setStatus("NEW");

            try {
                String json = objectMapper.writeValueAsString(
                        Map.of(
                                "eventId", event.getEventId(),
                                "eventType", event.getEventType(),
                                "instanceId", instance.getId(),
                                "definitionId", definition.getId(),
                                "userId", definition.getUserId(),
                                "scheduledAt",
                                instance.getScheduledAt().toString()
                        )
                );

                event.setPayload(json);

            } catch (JsonProcessingException ex) {
                throw new IllegalStateException(
                        "Failed to serialize instance event",
                        ex);
            }

            int outboxInserted = outboxMapper.insert(event);

            if (outboxInserted != 1) {
                throw new IllegalStateException(
                        "Failed to save instance event");
            }

            // 创建实例、写 Outbox 和推进定义仍在同一个事务里。
            advanceSchedule(definition, scheduledAt, nextTriggerAt);
            dispatched++;
        }

        return dispatched;
    }

    private void advanceSchedule(
            TaskDefinition definition, LocalDateTime scheduledAt, LocalDateTime nextTriggerAt
    ) {
        int updated = taskMapper.advanceSchedule(
                definition.getId(), definition.getTriggerType(), scheduledAt, nextTriggerAt
        );
        if (updated != 1) {
            throw new IllegalStateException("Failed to advance task schedule");
        }
    }
}
