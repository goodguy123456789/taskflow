package com.taskflow.task.controller;

import com.taskflow.task.service.SchedulerDispatchService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@ConditionalOnProperty(
        prefix = "taskflow.scheduler",
        name = "internal-dispatch-enabled",
        havingValue = "true"
)
public class SchedulerDispatchController {

    private final SchedulerDispatchService dispatchService;
    private final byte[] expectedToken;

    public SchedulerDispatchController(
            SchedulerDispatchService dispatchService,
            @Value("${TASKFLOW_SCHEDULER_TOKEN}") String token
    ) {
        if (token.isBlank()) {
            throw new IllegalArgumentException("TASKFLOW_SCHEDULER_TOKEN must not be blank");
        }
        this.dispatchService = dispatchService;
        this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping(
            value = "/internal/scheduler/dispatch-due",
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    public ResponseEntity<String> dispatchDue(
            @RequestHeader(name = "X-Taskflow-Scheduler-Token", required = false) String token
    ) {
        // 先校验令牌，校验失败时不进入派发事务。
        if (token == null || !MessageDigest.isEqual(
                expectedToken, token.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // HTTP 接口只负责接请求，数据库事务仍由原来的 Service 完成。
        int dispatched = dispatchService.scheduleDueBatch();
        return ResponseEntity.ok(Integer.toString(dispatched));
    }
}