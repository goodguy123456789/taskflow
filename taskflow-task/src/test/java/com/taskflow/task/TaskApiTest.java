package com.taskflow.task;

import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:taskflow;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-test.sql",
        "mybatis.mapper-locations=classpath:mapper/*.xml",
        "mybatis.configuration.map-underscore-to-camel-case=true"
})
@AutoConfigureMockMvc
class TaskApiTest {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final AtomicReference<Reply> REPLY = new AtomicReference<>();
    private static final AtomicReference<String> REQUEST_PATH = new AtomicReference<>();
    private static final HttpServer USERS = startUsers();

    @Autowired
    private MockMvc api;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void userServiceUrl(DynamicPropertyRegistry properties) {
        properties.add("taskflow.user-service.url",
                () -> "http://127.0.0.1:" + USERS.getAddress().getPort());
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM task_definition");
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
    void repeatedKeyReturnsSameTaskAndOnlyOneRow() throws Exception {
        String body = validRequest();
        MvcResult first = api.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.created").value(true))
                .andReturn();
        long id = new ObjectMapper().readTree(first.getResponse().getContentAsString())
                .at("/data/taskId").asLong();
        assertTrue(id > 0);
        assertEquals("GET /api/users/1", REQUEST_PATH.get());

        api.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value(id))
                .andExpect(jsonPath("$.data.created").value(false));

        api.perform(get("/api/tasks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.name").value("Daily Report"))
                .andExpect(jsonPath("$.data.handlerType").value("DEMO"))
                .andExpect(jsonPath("$.data.triggerType").value("ONCE"))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Singapore"))
                .andExpect(jsonPath("$.data.maxRetries").value(3));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM task_definition", Integer.class));
        Map<String, Object> saved = jdbc.queryForMap(
                "SELECT user_id, handler_type, trigger_type, idempotency_key FROM task_definition WHERE id = ?", id);
        assertEquals(1L, ((Number) saved.get("user_id")).longValue());
        assertEquals("DEMO", saved.get("handler_type"));
        assertEquals("ONCE", saved.get("trigger_type"));
        assertEquals(new ObjectMapper().readTree(body).get("idempotencyKey").asText(), saved.get("idempotency_key"));
    }

    @Test
    void sameKeyWithChangedRequestReturnsConflict() throws Exception {
        String body = validRequest();
        api.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        String changed = body.replace("Daily Report", "Different task");
        api.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(changed))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM task_definition", Integer.class));
    }

    @Test
    void differentKeysCreateDifferentTasks() throws Exception {
        api.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.created").value(true));
        api.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.created").value(true));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM task_definition", Integer.class));
    }

    @Test
    void missingTaskReturnsNotFound() throws Exception {
        api.perform(get("/api/tasks/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void invalidTaskIdReturnsBadRequest(long id) throws Exception {
        api.perform(get("/api/tasks/{id}", id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void preservesNotFoundStatusAndJsonBody() throws Exception {
        REPLY.set(new Reply(404, "{\"code\":404,\"message\":\"User not found\",\"data\":null}", 0));
        api.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(999)))
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
        api.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        assertNull(REQUEST_PATH.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "", "{\"userId\":\"invalid\",\"name\":\"demo\"}"})
    void unreadableRequestUsesUnifiedErrorBody(String request) throws Exception {
        api.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(request))
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
        api.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(502));
    }

    @Test
    void upstreamServerFailureBecomesBadGateway() throws Exception {
        REPLY.set(new Reply(500, "{\"code\":500,\"message\":\"failure\",\"data\":null}", 0));
        api.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(502));
    }

    @Test
    void configuredReadTimeoutBecomesServiceUnavailable() throws Exception {
        REPLY.set(new Reply(200, "{\"code\":200,\"message\":\"success\",\"data\":null}", 4500));
        api.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503));
    }

    private String validRequest() {
        return validRequest(1);
    }

    private String validRequest(long userId) {
        // The complete JSON-object payload is exercised against real MySQL in the local HTTP test.
        return "{\"userId\":" + userId
                + ",\"name\":\"Daily Report\",\"description\":\"Test task\""
                + ",\"handlerType\":\"DEMO\",\"triggerType\":\"ONCE\""
                + ",\"timezone\":\"Asia/Singapore\",\"nextTriggerAt\":\"2026-10-26T09:00:00\""
                + ",\"maxRetries\":3,\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}";
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
