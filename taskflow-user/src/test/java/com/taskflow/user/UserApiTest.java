package com.taskflow.user;

import com.taskflow.user.controller.UserController;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserApiTest {
    private final MockMvc api = MockMvcBuilders.standaloneSetup(new UserController()).build();

    @Test
    void existingUserReturnsWrappedUser() throws Exception {
        api.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("test-user"));
    }

    @Test
    void missingUserReturns404WithBody() throws Exception {
        api.perform(get("/api/users/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("User not found"));
    }
}