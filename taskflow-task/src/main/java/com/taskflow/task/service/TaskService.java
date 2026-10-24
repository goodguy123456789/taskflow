
package com.taskflow.task.service;

import com.taskflow.common.result.ApiResponse;
import com.taskflow.task.client.UserClient;
import com.taskflow.task.dto.UserInfo;

import feign.FeignException;
import feign.RetryableException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import lombok.Data;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final UserClient userClient;
    private final TaskMapper taskMapper;

    public String validateUserAndTask(
            Long userId, String taskName) {

        // 1. 校验请求参数
        if (userId == null || userId <= 0
                || taskName == null || taskName.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid task request");
        }

        ApiResponse<UserInfo> response;

        // 2. 远程查询用户
        try {
            response = userClient.getUser(userId);

        } catch (FeignException.NotFound ex) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "User not found", ex);

        } catch (RetryableException ex) {

            // 网络连接失败、部分超时场景
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "User Service is unreachable", ex);

        } catch (FeignException ex) {

            // 其他 HTTP 错误、编码或解码错误
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "User Service request failed", ex);
        }

        // 3. 检查返回内容
        if (response == null
                || response.code() != 200
                || response.data() == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unexpected User Service response");
        }

        // 4. 返回验证结果
        UserInfo user = response.data();

        return "User verified: "
                + user.username()
                + ", task: "
                + taskName;
    }
}
