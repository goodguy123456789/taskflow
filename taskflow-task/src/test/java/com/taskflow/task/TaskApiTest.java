package com.taskflow.task;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TaskApiTest {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final AtomicReference<Reply> REPLY = new AtomicReference<>();
    private static final AtomicReference<String> REQUEST_PATH = new AtomicReference<>();
    private static final HttpServer USERS = startUsers();

    @Autowired
    private MockMvc api;

    @DynamicPropertySource
    static void userServiceUrl(DynamicPropertyRegistry properties) {
        properties.add("taskflow.user-service.url",
                () -> "http://127.0.0.1:" + USERS.getAddress().getPort());
    }

    @BeforeEach
    void setUp() {
        REQUEST_PATH.set(null);
        REPLY.set(new Reply(200,
                "{\"code\":200,\"message\":\"success\",\"data\":{\"id\":1,\"username\":\"test-user\"}}", 0));
    }

    @AfterAll
    static void stopUsers() {
        USERS.stop(0);
        EXECUTOR.shutdownNow();
    }

    @Test
    void validatesExistingUserThroughRealFeignClient() throws Exception {
        api.perform(post("/api/tasks/validate-user").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"name\":\"demo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("User verified: test-user, task: demo"));
        assertEquals("GET /api/users/1", REQUEST_PATH.get());
    }

    @Test
    void preservesNotFoundStatusAndJsonBody() throws Exception {
        REPLY.set(new Reply(404, "{\"code\":404,\"message\":\"User not found\",\"data\":null}", 0));
        api.perform(post("/api/tasks/validate-user").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":999,\"name\":\"demo\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("User not found"));
        assertEquals("GET /api/users/999", REQUEST_PATH.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"userId\":0,\"name\":\"demo\"}",
            "{\"userId\":-1,\"name\":\"demo\"}",
            "{\"name\":\"demo\"}",
            "{\"userId\":1,\"name\":\" \"}",
            "{\"userId\":1}"
    })
    void rejectsInvalidTaskBeforeCallingUserService(String request) throws Exception {
        api.perform(post("/api/tasks/validate-user").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        assertNull(REQUEST_PATH.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "", "{\"userId\":\"invalid\",\"name\":\"demo\"}"})
    void unreadableRequestUsesUnifiedErrorBody(String request) throws Exception {
        api.perform(post("/api/tasks/validate-user").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());
        assertNull(REQUEST_PATH.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "{\"code\":200,\"message\":\"success\",\"data\":null}",
            "{\"code\":500,\"message\":\"failure\",\"data\":null}",
            "not-json"
    })
    void invalidUserResponseBecomesBadGateway(String response) throws Exception {
        REPLY.set(new Reply(200, response, 0));
        api.perform(post("/api/tasks/validate-user").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"name\":\"demo\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(502));
    }

    @Test
    void upstreamServerFailureBecomesBadGateway() throws Exception {
        REPLY.set(new Reply(500, "{\"code\":500,\"message\":\"failure\",\"data\":null}", 0));
        api.perform(post("/api/tasks/validate-user").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"name\":\"demo\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(502));
    }

    @Test
    void configuredReadTimeoutBecomesServiceUnavailable() throws Exception {
        REPLY.set(new Reply(200, "{\"code\":200,\"message\":\"success\",\"data\":null}", 4500));
        api.perform(post("/api/tasks/validate-user").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"name\":\"demo\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503));
    }

    private static HttpServer startUsers() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(EXECUTOR);
            server.createContext("/api/users", exchange -> {
                REQUEST_PATH.set(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
                Reply reply = REPLY.get();
                try {
                    if (reply.delayMillis() > 0) {
                        Thread.sleep(reply.delayMillis());
                    }
                    byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(reply.status(), body.length == 0 ? -1 : body.length);
                    if (body.length > 0) {
                        exchange.getResponseBody().write(body);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (IOException ex) {
                    // A timed-out Feign request can close the connection before the delayed reply.
                } finally {
                    exchange.close();
                }
            });
            server.start();
            return server;
        } catch (IOException ex) {
            EXECUTOR.shutdownNow();
            throw new ExceptionInInitializerError(ex);
        }
    }

    private record Reply(int status, String body, long delayMillis) {}
}
