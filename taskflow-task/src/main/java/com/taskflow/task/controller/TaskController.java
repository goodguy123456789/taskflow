
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
    public ResponseEntity<ApiResponse<Long>> createTask(
            @RequestBody CreateTaskRequest request) {

        Long taskId = taskService.createTask(
                request.userId(),
                request.name()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(taskId));
    }

    @GetMapping("/{id}")
    public ApiResponse<TaskDefinition> getTask(
            @PathVariable Long id) {

        TaskDefinition task = taskService.getTask(id);

        return ApiResponse.success(task);
    }
}
