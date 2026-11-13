package com.taskflow.task.client;

import com.taskflow.common.result.ApiResponse;
import com.taskflow.task.dto.UserInfo;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "taskflow-user",
        url = "${taskflow.user-service.url}"
)
public interface UserClient {

    @GetMapping("/api/users/{id}")
    ApiResponse<UserInfo> getUser(
            @PathVariable("id") Long userId
    );
}
