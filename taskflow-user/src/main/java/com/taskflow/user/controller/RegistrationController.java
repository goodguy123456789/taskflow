package com.taskflow.user.controller;

import com.taskflow.common.result.ApiResponse;
import com.taskflow.user.mapper.UserMapper;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RegistrationController {

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,64}");

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public RegistrationController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @PostMapping("/api/users/register")
    public ResponseEntity<ApiResponse<RegisteredUser>> register(
            @RequestBody RegisterRequest request
    ) {
        String username = request.username();
        if (username == null || !USERNAME.matcher(username).matches()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure(400, "Invalid username"));
        }

        String password = request.password();
        if (password == null || password.isBlank() || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure(400, "Invalid password"));
        }

        // 先生成哈希，再写入数据库。
        String passwordHash = passwordEncoder.encode(password);
        try {
            userMapper.insertActiveUser(username, passwordHash);
        } catch (DuplicateKeyException ex) {
            // 同名请求并发到达时，由数据库唯一索引决定哪条能插入。
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.failure(409, "Username already exists"));
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(new RegisteredUser(username)));
    }

    public record RegisterRequest(String username, String password) {
    }

    public record RegisteredUser(String username) {
    }
}