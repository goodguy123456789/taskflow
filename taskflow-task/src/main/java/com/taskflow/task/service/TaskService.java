
package com.taskflow.task.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.common.result.ApiResponse;
import com.taskflow.task.client.UserClient;
import com.taskflow.task.dto.CreateTaskRequest;
import com.taskflow.task.dto.CreateTaskResult;
import com.taskflow.task.dto.UserInfo;
import com.taskflow.task.entity.TaskDefinition;
import com.taskflow.task.mapper.TaskMapper;
import com.taskflow.task.scheduler.CronTriggerCalculator;

import feign.FeignException;
import feign.RetryableException;
import lombok.RequiredArgsConstructor;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final UserClient userClient;
    private final TaskWriter taskWriter;
    private final TaskMapper taskMapper;
    private final ObjectMapper objectMapper;

    // 不加 @Transactional：远程调用在事务外
    public CreateTaskResult createTask(CreateTaskRequest request) {

        TaskDefinition task = buildTask(request);

        // 1. 同一用户、同一 key，先查询是否已创建
        TaskDefinition existing =
                taskMapper.findByUserAndIdempotencyKey(
                        task.getUserId(),
                        task.getIdempotencyKey()
                );

        if (existing != null) {
            return resolveDuplicate(existing, task);
        }

        // 2. 远程验证用户
        verifyUser(task.getUserId());

        // 3. 调用 TaskWriter 的事务方法
        try {
            Long id = taskWriter.insertTask(task);
            return new CreateTaskResult(id, true);

        } catch (DuplicateKeyException ex) {

            // 失败事务已经退出，再读取并发请求创建的记录
            TaskDefinition saved =
                    taskMapper.findByUserAndIdempotencyKey(
                            task.getUserId(),
                            task.getIdempotencyKey()
                    );

            if (saved == null) {
                throw ex;
            }

            return resolveDuplicate(saved, task);
        }
    }

    private TaskDefinition buildTask(CreateTaskRequest request) {

        if (request == null
                || request.userId() == null
                || request.userId() <= 0) {
            throw badRequest("Invalid user ID");
        }

        String name = requiredText(
                request.name(), 128, "name");

        String handlerType = requiredText(
                request.handlerType(), 64, "handlerType");

        String description = request.description();

        if (description != null && description.length() > 512) {
            throw badRequest("description is too long");
        }

        String triggerType = request.triggerType();
        if (!"ONCE".equals(triggerType) && !"CRON".equals(triggerType)) {
            throw badRequest("triggerType must be ONCE or CRON");
        }

        String timezone = request.timezone() == null
                ? "UTC"
                : requiredText(request.timezone(), 64, "timezone");

        ZoneId taskZone;
        try {
            taskZone = ZoneId.of(timezone);
        } catch (DateTimeException ex) {
            throw badRequest("Invalid timezone");
        }

        String cronExpression = null;
        LocalDateTime nextTriggerAt;
        if ("CRON".equals(triggerType)) {
            if (request.nextTriggerAt() != null) {
                throw badRequest("nextTriggerAt must be empty for CRON");
            }
            cronExpression = requiredText(request.cronExpression(), 128, "cronExpression");

            // 首次触发时间由服务端计算，返回值已经是数据库使用的 UTC。
            try {
                nextTriggerAt = CronTriggerCalculator.nextUtcAfter(
                        cronExpression, LocalDateTime.now(ZoneOffset.UTC), taskZone);
            } catch (IllegalArgumentException ex) {
                throw badRequest("Invalid cronExpression: " + ex.getMessage());
            }
        } else {
            if (request.cronExpression() != null && !request.cronExpression().isBlank()) {
                throw badRequest("cronExpression must be empty for ONCE");
            }
            LocalDateTime requestedTime = request.nextTriggerAt();
            if (requestedTime == null) {
                throw badRequest("nextTriggerAt is required");
            }

            // ONCE 的请求时间按任务时区解释，再转成 UTC，并保留毫秒精度。
            nextTriggerAt = requestedTime.atZone(taskZone)
                    .withZoneSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime()
                    .truncatedTo(ChronoUnit.MILLIS);
        }


        int maxRetries = request.maxRetries() == null
                ? 3
                : request.maxRetries();

        if (maxRetries < 0) {
            throw badRequest("maxRetries cannot be negative");
        }

        String key = normalizeKey(request.idempotencyKey());

        TaskDefinition task = new TaskDefinition();
        task.setUserId(request.userId());
        task.setName(name);
        task.setDescription(description);
        task.setHandlerType(handlerType);
        task.setTriggerType(triggerType);
        task.setCronExpression(cronExpression);
        task.setTimezone(timezone);
        task.setNextTriggerAt(nextTriggerAt);
        task.setPayload(
                request.payload() == null
                        ? null
                        : request.payload().toString()
        );
        task.setMaxRetries(maxRetries);
        task.setStatus("ENABLED");
        task.setIdempotencyKey(key);

        return task;
    }

    private String requiredText(
            String text, int maxLength, String field) {

        if (text == null || text.isBlank()) {
            throw badRequest(field + " is required");
        }

        String value = text.trim();

        if (value.length() > maxLength) {
            throw badRequest(field + " is too long");
        }

        return value;
    }

    private String normalizeKey(String rawKey) {

        if (rawKey == null) {
            throw badRequest("idempotencyKey is required");
        }

        String key = rawKey.trim().toLowerCase(Locale.ROOT);

        try {
            UUID uuid = UUID.fromString(key);

            if (!uuid.toString().equals(key)) {
                throw new IllegalArgumentException();
            }

            return key;

        } catch (IllegalArgumentException ex) {
            throw badRequest("Invalid idempotencyKey");
        }
    }

    private CreateTaskResult resolveDuplicate(
            TaskDefinition existing,
            TaskDefinition requested) {

        boolean sameRequest =
                Objects.equals(existing.getUserId(),
                               requested.getUserId())
                && Objects.equals(existing.getName(),
                                  requested.getName())
                && Objects.equals(existing.getDescription(),
                                  requested.getDescription())
                && Objects.equals(existing.getHandlerType(),
                                  requested.getHandlerType())
                && Objects.equals(existing.getTriggerType(),
                                  requested.getTriggerType())
                && Objects.equals(existing.getCronExpression(),
                                  requested.getCronExpression())
                && Objects.equals(existing.getTimezone(),
                                  requested.getTimezone())
                && sameInitialSchedule(existing, requested)
                && Objects.equals(existing.getMaxRetries(),
                                  requested.getMaxRetries())
                && Objects.equals(jsonTree(existing.getPayload()),
                                  jsonTree(requested.getPayload()));

        if (!sameRequest) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency key reused with different request");
        }

        return new CreateTaskResult(existing.getId(), false);
    }

    private boolean sameInitialSchedule(TaskDefinition existing, TaskDefinition requested) {
        if ("CRON".equals(requested.getTriggerType())) {
            // CRON 的首次触发时间由服务端生成，表达式和时区已在外层比较。
            return true;
        }

        LocalDateTime original = existing.getNextTriggerAt();
        if (original == null) {
            // ONCE 已派发。实例保留了定义当初指定的 UTC 时间。
            original = taskMapper.findDispatchedOnceScheduledAt(existing.getId());
        }

        // 找不到唯一的原始时间时，不能证明两次请求相同。
        return original != null && original.equals(requested.getNextTriggerAt());
    }


    private JsonNode jsonTree(String json) {

        if (json == null) {
            return null;
        }

        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Invalid JSON stored in database", ex);
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST, message);
    }

    private void verifyUser(Long userId) {

        ApiResponse<UserInfo> response;

        try {
            response = userClient.getUser(userId);

        } catch (FeignException.NotFound ex) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "User not found", ex);

        } catch (RetryableException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "User Service is unreachable", ex);

        } catch (FeignException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "User Service request failed", ex);
        }

        if (response == null
                || response.code() != 200
                || response.data() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unexpected User Service response");
        }
    }

    @Transactional(readOnly = true)
    public TaskDefinition getTask(Long id) {

        if (id == null || id <= 0) {
            throw badRequest("Invalid task ID");
        }

        TaskDefinition task = taskMapper.findById(id);

        if (task == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Task not found");
        }

        return task;
    }
}
