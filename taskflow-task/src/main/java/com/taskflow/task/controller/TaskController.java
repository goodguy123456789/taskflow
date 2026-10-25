
package com.taskflow.task.controller;

import com.taskflow.common.result.ApiResponse;
import com.taskflow.task.dto.CreateTaskRequest;
import com.taskflow.task.entity.TaskDefinition;
import com.taskflow.task.service.TaskService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

 @PostMapping
public ResponseEntity<ApiResponse<CreateTaskResult>> createTask(
        @RequestBody CreateTaskRequest request) {

    CreateTaskResult result = taskService.createTask(request);

    return ResponseEntity
            .status(result.created()
                    ? HttpStatus.CREATED
                    : HttpStatus.OK)
            .body(ApiResponse.success(result));
}
    @GetMapping("/{id}")
    public ApiResponse<TaskDefinition> getTask(
            @PathVariable Long id) {

        TaskDefinition task = taskService.getTask(id);

        return ApiResponse.success(task);
    }
}
