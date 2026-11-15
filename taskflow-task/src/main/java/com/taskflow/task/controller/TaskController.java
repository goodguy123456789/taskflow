package com.taskflow.task.controller;

import com.taskflow.common.result.ApiResponse;
import com.taskflow.task.dto.CreateTaskRequest;
import com.taskflow.task.dto.CreateTaskResult;
import com.taskflow.task.entity.TaskDefinition;
import com.taskflow.task.security.GatewayIdentity;
import com.taskflow.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final GatewayIdentity gatewayIdentity;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateTaskResult>> createTask(
            @RequestHeader(name = GatewayIdentity.TOKEN_HEADER, required = false) String gatewayToken,
            @RequestHeader(name = GatewayIdentity.USER_ID_HEADER, required = false) String forwardedUserId,
            @RequestBody CreateTaskRequest request
    ) {
        long userId = gatewayIdentity.requireUserId(gatewayToken, forwardedUserId);
        CreateTaskResult result = taskService.createTask(request, userId);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    public ApiResponse<TaskDefinition> getTask(
            @RequestHeader(name = GatewayIdentity.TOKEN_HEADER, required = false) String gatewayToken,
            @RequestHeader(name = GatewayIdentity.USER_ID_HEADER, required = false) String forwardedUserId,
            @PathVariable Long id
    ) {
        long userId = gatewayIdentity.requireUserId(gatewayToken, forwardedUserId);
        return ApiResponse.success(taskService.getTask(id, userId));
    }
}