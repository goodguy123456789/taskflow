
package com.taskflow.task.service;

import com.taskflow.common.result.ApiResponse;
import com.taskflow.task.client.UserClient;
import com.taskflow.task.dto.UserInfo;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TaskService {

    private final UserClient userClient;

    // 构造器注入 UserClient Bean
    public TaskService(UserClient userClient) {
        this.userClient = userClient;
    }

    // 验证用户是否存在，并校验任务名称
    public String validateUserAndTask(
            Long userId, String taskName) {

        // 1. 校验请求参数
        if (userId == null || userId <= 0
                || taskName == null
                || taskName.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid task request"
            );
        }

        ApiResponse<UserInfo> response;

        // 2. 调用 User Service
        try {
            response = userClient.getUser(userId);

        } catch (HttpClientErrorException.NotFound ex) {

            // User Service 返回 HTTP 404
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "User not found",
                    ex
            );

        } catch (ResourceAccessException ex) {

            // 连接失败或部分网络超时场景
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "User Service is unreachable",
                    ex
            );

        } catch (RestClientException ex) {

            // 其他远程 HTTP 调用错误
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "User Service returned an invalid response",
                    ex
            );
        }

        // 3. 检查响应是否为空
        if (response == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Empty User Service response"
            );
        }

        // 4. 检查业务响应是否正常
        if (response.code() != 200
                || response.data() == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unexpected User Service response"
            );
        }

        // 5. 用户验证成功
        UserInfo user = response.data();

        return "User verified: "
                + user.username()
                + ", task: "
                + taskName;
    }
}
