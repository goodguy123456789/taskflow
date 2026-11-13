package com.taskflow.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:taskflow_users;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureMockMvc
class UserApiTest {
    @Autowired
    private MockMvc api;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void prepareUsers() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sys_user (
                    id BIGINT PRIMARY KEY, username VARCHAR(64) NOT NULL,
                    password_hash VARCHAR(255) NOT NULL, status VARCHAR(20) NOT NULL
                )
                """);
        jdbc.update("DELETE FROM sys_user");
        jdbc.update("INSERT INTO sys_user VALUES (?, ?, ?, ?)", 7L, "db-user", "fixture-hash", "ACTIVE");
        jdbc.update("INSERT INTO sys_user VALUES (?, ?, ?, ?)", 8L, "disabled-user", "fixture-hash", "DISABLED");
    }

    @Test
    void existingUserReturnsWrappedUser() throws Exception {
        api.perform(get("/api/users/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.username").value("db-user"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void missingUserReturns404WithBody() throws Exception {
        api.perform(get("/api/users/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void disabledUserCannotPassLookup() throws Exception {
        api.perform(get("/api/users/8")).andExpect(status().isNotFound());
    }
}
