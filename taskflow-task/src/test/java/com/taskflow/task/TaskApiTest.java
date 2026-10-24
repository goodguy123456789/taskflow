package com.taskflow.task;

import com.taskflow.task.client.UserClient;
import com.taskflow.task.controller.TaskController;
import com.taskflow.task.exception.GlobalExceptionHandler;
import com.taskflow.task.service.TaskService;
import java.net.ConnectException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TaskApiTest {
    private MockRestServiceServer users;
    private MockMvc api;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://user-service");
        users = MockRestServiceServer.bindTo(builder).build();
        TaskService service = new TaskService(new UserClient(builder.build()));
        api = MockMvcBuilders.standaloneSetup(new TaskController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void validatesExistingUserThroughClientAndService() throws Exception {
        users.expect(requestTo("http://user-service/api/users/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"code\":200,\"message\":\"success\",\"data\":{\"id\":1,\"username\":\"test-user\"}}",
                        MediaType.APPLICATION_JSON));
        api.perform(post("/api/tasks/validate-user").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"name\":\"demo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("User verified: test-user, task: demo"));
        users.verify();
    }

    @Test
    void preservesNotFoundStatusAndJsonBody() throws Exception {
        users.expect(requestTo("http://user-service/api/users/999"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":404,\"message\":\"User not found\",\"data\":null}"));
        api.perform(post("/api/tasks/validate-user").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":999,\"name\":\"demo\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("User not found"));
        users.verify();
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
        users.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "", "{\"userId\":\"invalid\",\"name\":\"demo\"}"})
    void unreadableRequestUsesUnifiedErrorBody(String request) throws Exception {
        api.perform(post("/api/tasks/validate-user").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());
        users.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "{\"code\":200,\"message\":\"success\",\"data\":null}",
            "{\"code\":500,\"message\":\"failure\",\"data\":null}",
            "not-json"
    })
    void invalidUserResponseBecomesBadGateway(String response) throws Exception {
        users.expect(requestTo("http://user-service/api/users/1"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        api.perform(post("/api/tasks/validate-user").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"name\":\"demo\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(502));
        users.verify();
    }

    @Test
    void upstreamServerFailureBecomesBadGateway() throws Exception {
        users.expect(requestTo("http://user-service/api/users/1")).andRespond(withServerError());
        api.perform(post("/api/tasks/validate-user").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"name\":\"demo\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(502));
        users.verify();
    }

    @Test
    void unreachableUserServiceBecomesServiceUnavailable() throws Exception {
        users.expect(requestTo("http://user-service/api/users/1"))
                .andRespond(withException(new ConnectException("Connection refused")));
        api.perform(post("/api/tasks/validate-user").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"name\":\"demo\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503));
        users.verify();
    }
}