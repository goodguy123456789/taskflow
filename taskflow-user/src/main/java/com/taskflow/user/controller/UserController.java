
package com.taskflow.user.controller;

import com.taskflow.common.result.ApiResponse;
import com.taskflow.user.dto.UserInfo;
import com.taskflow.user.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserMapper userMapper;

    public UserController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.success("User Service is running!");
    }


    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserInfo>> getUser(@PathVariable Long id) {
        UserInfo user = userMapper.findActiveById(id);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.failure(404, "User not found"));
        }
        return ResponseEntity.ok(ApiResponse.success(user));
    }
}
