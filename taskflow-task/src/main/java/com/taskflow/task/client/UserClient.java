
package com.taskflow.task.client;

import com.taskflow.common.result.ApiResponse;
import com.taskflow.task.dto.UserInfo;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class UserClient {

    private final RestClient userRestClient;

    public UserClient(RestClient userRestClient) {
        this.userRestClient = userRestClient;
    }

    public ApiResponse<UserInfo> getUser(Long userId) {

        return userRestClient.get()
                .uri("/api/users/{id}", userId)
                .retrieve()
                .body(new ParameterizedTypeReference<
                        ApiResponse<UserInfo>>() {});
    }
}
