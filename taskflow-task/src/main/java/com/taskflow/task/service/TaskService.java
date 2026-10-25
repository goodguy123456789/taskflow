
package com.taskflow.task.service;

import com.taskflow.common.result.ApiResponse;
import com.taskflow.task.client.UserClient;
import com.taskflow.task.dto.UserInfo;
import com.taskflow.task.entity.TaskDefinition;
import com.taskflow.task.mapper.TaskMapper;

import feign.FeignException;
import feign.RetryableException;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final UserClient userClient;
    private final TaskMapper taskMapper;

    @Transactional
    public Long createTask(Long userId, String taskName) {

        // 1. 校验请求参数
        if (userId == null || userId <= 0
                || taskName == null
                || taskName.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid task request");
        }

        // 2. 通过 Feign 验证用户
        ApiResponse<UserInfo> response;

        try {
            response = userClient.getUser(userId);

        } catch (FeignException.NotFound ex) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "User not found", ex);

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

        // 3. 创建任务实体
        TaskDefinition task = new TaskDefinition();
        task.setOwnerUserId(userId);
        task.setName(taskName);
        task.setStatus("ENABLED");

        // 4. 执行数据库插入
        int rows = taskMapper.insert(task);

        if (rows != 1 || task.getId() == null) {
            throw new IllegalStateException(
                    "Failed to create task");
        }

        // 5. 返回数据库生成的任务 ID
        return task.getId();
    }

    @Transactional(readOnly = true)
    public TaskDefinition getTask(Long id) {

        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid task ID");
        }

        TaskDefinition task = taskMapper.findById(id);

        if (task == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Task not found");
        }

        return task;
    }
}
