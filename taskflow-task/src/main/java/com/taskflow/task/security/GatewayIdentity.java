package com.taskflow.task.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class GatewayIdentity {

    public static final String TOKEN_HEADER = "X-Taskflow-Gateway-Token";
    public static final String USER_ID_HEADER = "X-Taskflow-User-Id";

    private final byte[] expectedToken;

    public GatewayIdentity(@Value("${TASKFLOW_GATEWAY_TOKEN:}") String token) {
        this.expectedToken = token.isBlank()
                ? new byte[0]
                : token.getBytes(StandardCharsets.UTF_8);
    }

    public long requireUserId(String gatewayToken, String userIdHeader) {
        // 运行环境还没配置内部令牌时，任务接口暂停接收请求。
        if (expectedToken.length == 0) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Gateway authentication is unavailable");
        }

        // 先确认调用方持有 Gateway 与 Task 共享的内部令牌。
        if (gatewayToken == null || !MessageDigest.isEqual(
                expectedToken, gatewayToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Untrusted gateway request");
        }

        // 只有内部令牌通过检查后，才能使用请求头里的用户身份。
        if (userIdHeader != null) {
            try {
                long userId = Long.parseLong(userIdHeader);
                if (userId > 0) {
                    return userId;
                }
            } catch (NumberFormatException ex) {
                // 非数字或超出 long 范围时，走下面的错误响应。
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid gateway user ID");
    }
}