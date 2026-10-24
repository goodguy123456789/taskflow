
package com.taskflow.user.controller;

import com.taskflow.common.result.ApiResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.success("User Service is running!");
    }


@GetMapping("/{id}")
public ResponseEntity<ApiResponse<UserInfo>> getUser(
        @PathVariable Long id) {

    if (id == 1L) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        new UserInfo(1L, "test-user")));
    }

    return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.<UserInfo>failure(
                    404, "User not found"));
}

    public record UserInfo(Long id, String username) {}
}
