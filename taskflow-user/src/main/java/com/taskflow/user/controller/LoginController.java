package com.taskflow.user.controller;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.taskflow.common.result.ApiResponse;
import com.taskflow.user.mapper.UserMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginController {

    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final byte[] signingKey;

    public LoginController(
            UserMapper userMapper,
            @Value("${TASKFLOW_JWT_SECRET_BASE64:}") String encodedKey
    ) {
        this.userMapper = userMapper;
        // 缺少密钥时保留 User 查询和注册功能，登录接口返回 503。
        this.signingKey = encodedKey.isBlank()
                ? new byte[0]
                : Base64.getDecoder().decode(encodedKey);
        if (!encodedKey.isBlank() && signingKey.length != 32) {
            throw new IllegalArgumentException("JWT signing key must be 32 bytes");
        }
    }

    @PostMapping("/api/users/login")
    public ResponseEntity<ApiResponse<LoginResult>> login(
            @RequestBody LoginRequest request
    ) throws JOSEException {
        // 1. 先检查服务端是否配置了签名密钥。缺少密钥属于服务不可用，返回 503。
        if (signingKey.length == 0) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.failure(503, "Login is unavailable"));
        }

        // 2. 读取 JSON 请求体。格式不合法时直接返回 400，避免查库和计算 BCrypt。
        String username = request.username();
        String password = request.password();
        if (username == null || username.isBlank() || username.length() > 64
                || password == null || password.isBlank()
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure(400, "Invalid login request"));
        }

        // 3. 通过 UserMapper.xml 只查 ACTIVE 用户，用 matches 比对输入密码和库里的哈希。
        UserMapper.LoginUser user = userMapper.findActiveForLogin(username);
        // 用户不存在、已禁用或密码错误都返回同一条 401 消息。
        if (user == null || !passwordEncoder.matches(password, user.passwordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure(401, "Invalid username or password"));
        }

        // 4. 生成令牌声明：iss 是签发方，aud 是接收方，sub 是数据库里的用户 ID。
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("taskflow-user")
                .audience("taskflow-api")
                .subject(user.id().toString())
                // iat 是签发时间，exp 是到期时间，两者相差 15 分钟。
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(TOKEN_LIFETIME)))
                .build();

        // 5. 用 HS256 对令牌头和声明签名。声明可被读取，因此这里只放身份和时间信息。
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(signingKey));

        // 6. 返回令牌字符串、Bearer 类型和有效秒数，供客户端构造 Authorization 请求头。
        return ResponseEntity.ok(ApiResponse.success(
                new LoginResult(jwt.serialize(), "Bearer", TOKEN_LIFETIME.toSeconds())));
    }

    public record LoginRequest(String username, String password) {
    }

    public record LoginResult(String accessToken, String tokenType, long expiresIn) {
    }
}