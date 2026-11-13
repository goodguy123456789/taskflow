package com.taskflow.task.controller;

import com.taskflow.common.result.ApiResponse;
import com.taskflow.task.service.ReadyDeadLetterReplayService;
import com.taskflow.task.service.ReadyDeadLetterReplayService.Result;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.amqp.AmqpException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "taskflow.dlq", name = "replay-enabled", havingValue = "true")
public class ReadyDeadLetterReplayController {

    private final ReadyDeadLetterReplayService replayService;
    private final byte[] expectedToken;

    public ReadyDeadLetterReplayController(
            ReadyDeadLetterReplayService replayService,
            @Value("${TASKFLOW_DLQ_REPLAY_TOKEN:}") String token
    ) {
        if (token.isBlank()) {
            throw new IllegalArgumentException("TASKFLOW_DLQ_REPLAY_TOKEN must not be blank");
        }
        this.replayService = replayService;
        this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/internal/admin/dead-letters/ready/replay/{eventId}")
    public ResponseEntity<ApiResponse<Result>> replay(
            @PathVariable("eventId") String eventId,
            @RequestHeader(name = "X-Taskflow-Dlq-Replay-Token", required = false) String token
    ) {
        // 校验通过后才允许 Service 读取和确认队列中的消息。
        if (token == null || !MessageDigest.isEqual(expectedToken, token.getBytes(StandardCharsets.UTF_8))) {
            return failure(HttpStatus.FORBIDDEN, "FORBIDDEN");
        }
        if (eventId.isBlank()) {
            return failure(HttpStatus.BAD_REQUEST, "EVENT_ID_REQUIRED");
        }

        final Result result;
        try {
            result = replayService.replayHead(eventId);
        } catch (AmqpException ex) {
            // 连接或通道异常时，客户端需要核对队头和发布结果。
            return failure(HttpStatus.SERVICE_UNAVAILABLE, "REPLAY_RESULT_UNKNOWN");
        }

        return switch (result) {
            case REPLAYED -> ResponseEntity.ok(ApiResponse.success(result));
            case EMPTY -> failure(HttpStatus.NOT_FOUND, result.name());
            case HEAD_MISMATCH, INSTANCE_NOT_ELIGIBLE -> failure(HttpStatus.CONFLICT, result.name());
            case INVALID_MESSAGE -> failure(HttpStatus.UNPROCESSABLE_ENTITY, result.name());
            case PUBLISH_FAILED -> failure(HttpStatus.SERVICE_UNAVAILABLE, result.name());
        };
    }

    private ResponseEntity<ApiResponse<Result>> failure(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.failure(status.value(), message));
    }
}