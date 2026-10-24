
package com.taskflow.task.controller;

import com.taskflow.common.result.ApiResponse;
import com.taskflow.task.service.TaskService;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/validate-user")
    public ApiResponse<String> validateUser(
            @RequestBody CreateTaskRequest request) {

        String result = taskService.validateUserAndTask(
                request.userId(),
                request.name()
        );

        return ApiResponse.success(result);
    }

    public record CreateTaskRequest(
            Long userId,
            String name
    ) {}
}
