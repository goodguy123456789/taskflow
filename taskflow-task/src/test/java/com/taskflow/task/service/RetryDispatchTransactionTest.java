package com.taskflow.task.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.task.mapper.OutboxMapper;
import com.taskflow.task.mapper.TaskInstanceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(RetryDispatchTransactionTest.TestConfig.class)
class RetryDispatchTransactionTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RetryDispatchService service;

    @BeforeEach
    void prepare() {
        jdbc.execute("DROP TABLE IF EXISTS task_outbox");
        jdbc.execute("DROP TABLE IF EXISTS task_execution");
        jdbc.execute("DROP TABLE IF EXISTS task_instance");
        jdbc.execute("CREATE TABLE task_instance (id BIGINT PRIMARY KEY, definition_id BIGINT NOT NULL, user_id BIGINT NOT NULL, scheduled_at TIMESTAMP(3) NOT NULL, status VARCHAR(20) NOT NULL, attempt_count INT NOT NULL)");
        jdbc.execute("CREATE TABLE task_execution (instance_id BIGINT NOT NULL, attempt_no INT NOT NULL, status VARCHAR(20) NOT NULL, end_time TIMESTAMP(3), UNIQUE(instance_id, attempt_no))");
        jdbc.execute("CREATE TABLE task_outbox (id BIGINT AUTO_INCREMENT PRIMARY KEY, event_id VARCHAR(64) NOT NULL UNIQUE, aggregate_type VARCHAR(64) NOT NULL, aggregate_id BIGINT NOT NULL, event_type VARCHAR(64) NOT NULL, payload JSON NOT NULL)");
        jdbc.update("INSERT INTO task_instance VALUES (100, 10, 1, TIMESTAMP '2026-10-26 01:00:00', 'RETRY_WAIT', 1)");
        jdbc.update("INSERT INTO task_execution VALUES (100, 1, 'FAILED', UTC_TIMESTAMP(3))");
    }

    @Test
    void onlyQueuesDueRetryOnceAndCreatesOutbox() {
        assertEquals(0, service.dispatchDue(30, 10));
        assertEquals("RETRY_WAIT", jdbc.queryForObject("SELECT status FROM task_instance WHERE id=100", String.class));
        jdbc.update("UPDATE task_execution SET end_time = TIMESTAMPADD(SECOND, -60, UTC_TIMESTAMP(3)) WHERE instance_id=100");
        assertEquals(1, service.dispatchDue(30, 10));
        assertEquals("QUEUED", jdbc.queryForObject("SELECT status FROM task_instance WHERE id=100", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM task_outbox WHERE aggregate_id=100 AND event_type='TASK_INSTANCE_READY'", Integer.class));
        assertEquals(0, service.dispatchDue(30, 10));
    }

    @Test
    void rollsBackQueueWhenOutboxInsertFails() {
        jdbc.update("UPDATE task_execution SET end_time = TIMESTAMPADD(SECOND, -60, UTC_TIMESTAMP(3)) WHERE instance_id=100");
        jdbc.execute("DROP TABLE task_outbox");
        assertThrows(RuntimeException.class, () -> service.dispatchDue(30, 10));
        assertEquals("RETRY_WAIT", jdbc.queryForObject("SELECT status FROM task_instance WHERE id=100", String.class));
    }

    @Configuration
    @Import(WorkerResultTransactionTest.TestConfig.class)
    static class TestConfig {
        @Bean OutboxMapper outboxMapper(SqlSessionTemplate template) { return template.getMapper(OutboxMapper.class); }
        @Bean RetryDispatchService retryDispatchService(TaskInstanceMapper instanceMapper, OutboxMapper outboxMapper) {
            return new RetryDispatchService(instanceMapper, outboxMapper, new ObjectMapper());
        }
    }
}
