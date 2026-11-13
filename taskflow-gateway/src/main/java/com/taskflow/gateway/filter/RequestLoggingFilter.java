package com.taskflow.gateway.filter;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();
        //mutate()拿到一个构造器
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(REQUEST_ID_HEADER, requestId))
                .build();

        // 在提交响应头之前记录最终状态，并把同一个请求 ID 返回给客户端。
        var response = exchange.getResponse();
        response.beforeCommit(() -> {
            response.getHeaders().set(REQUEST_ID_HEADER, requestId);
            HttpStatusCode status = response.getStatusCode();
            int statusCode = status == null ? 200 : status.value();
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            log.info("Gateway request: requestId={}, method={}, path={}, status={}, elapsedMs={}",
                    requestId, request.getMethod(), request.getURI().getRawPath(), statusCode, elapsedMs);
            return Mono.empty();
        });

        // 后续过滤器和上游服务读取带有 X-Request-Id 的新请求。
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        // 早于 Gateway 默认的响应写入过滤器（顺序 -1）。
        return -2;
    }
}