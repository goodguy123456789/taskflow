package com.taskflow.gateway.filter;

import java.time.Duration;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

@Component
public class TaskJwtFilter implements GlobalFilter, Ordered {

    private static final String TASK_ROUTE_ID = "taskflow-task";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ISSUER = "taskflow-user";
    private static final String AUDIENCE = "taskflow-api";
    private static final String GATEWAY_TOKEN_HEADER = "X-Taskflow-Gateway-Token";
    private static final String USER_ID_HEADER = "X-Taskflow-User-Id";

    private final String gatewayToken;
    private final NimbusReactiveJwtDecoder decoder;

    public TaskJwtFilter(
            @Value("${TASKFLOW_JWT_SECRET_BASE64:}") String encodedKey,
            @Value("${TASKFLOW_GATEWAY_TOKEN:}") String gatewayToken) {
        this.gatewayToken = gatewayToken;
        if (encodedKey.isBlank()) {
            // 网关仍能启动。缺少验签密钥时，Task 路由返回 503。
            this.decoder = null;
            return;
        }

        byte[] key = Base64.getDecoder().decode(encodedKey);
        if (key.length != 32) {
            throw new IllegalArgumentException("JWT signing key must be 32 bytes");
        }

        // 固定使用 HS256，与 User 服务的签名方式一致。
        var secretKey = new SecretKeySpec(key, "HmacSHA256");
        this.decoder = NimbusReactiveJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        // 校验签发方和时间，允许的时钟偏差设为零。
        this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtIssuerValidator(ISSUER),
                new JwtTimestampValidator(Duration.ZERO)));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        if (route == null || !TASK_ROUTE_ID.equals(route.getId())) {
            return chain.filter(exchange);
        }

        if (decoder == null || gatewayToken.isBlank()) {
            exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return exchange.getResponse().setComplete();
        }
        String authorization = exchange.getRequest().getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (authorization == null
                || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return unauthorized(exchange);
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return unauthorized(exchange);
        }

        // decode 成功意味着签名、签发方和时间校验通过，再检查 TaskFlow 所需字段。
        return decoder.decode(token)
                // 只有通过验签和字段检查，才从 sub 取得用户 ID。
                .map(jwt -> hasRequiredClaims(jwt) ? jwt.getSubject() : "")
                .onErrorReturn(JwtException.class, "")
                .flatMap(userId -> {
                    if (userId.isEmpty()) {
                        return unauthorized(exchange);
                    }

                    var forwardedRequest = exchange.getRequest().mutate()
                            .headers(headers -> {
                                // set 覆盖客户端传来的值。用户 ID 来自已验签的 sub。
                                headers.set(USER_ID_HEADER, userId);
                                headers.set(GATEWAY_TOKEN_HEADER, gatewayToken);
                                // Task 使用内部凭据，本次转发可以移除客户端令牌。
                                headers.remove(HttpHeaders.AUTHORIZATION);
                            })
                            .build();

                    return chain.filter(exchange.mutate().request(forwardedRequest).build());
                });
    }

    private static boolean hasRequiredClaims(Jwt jwt) {
        if (jwt.getExpiresAt() == null
                || jwt.getAudience() == null
                || !jwt.getAudience().contains(AUDIENCE)
                || jwt.getSubject() == null) {
            return false;
        }

        try {
            // User 服务把数据库中的正整数 ID 写入 sub。
            return Long.parseLong(jwt.getSubject()) > 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static Mono<Void> unauthorized(ServerWebExchange exchange) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        // 请求日志过滤器是 -2，先注册日志回调，因此也能记录这里返回的 401。
        return -1;
    }
}