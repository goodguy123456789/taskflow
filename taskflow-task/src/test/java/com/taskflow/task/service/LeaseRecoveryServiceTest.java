package com.taskflow.task.service;

import com.taskflow.task.mapper.TaskExecutionMapper;
import com.taskflow.task.mapper.TaskInstanceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(LeaseRecoveryServiceTest.TestConfig.class)
class LeaseRecoveryServiceTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LeaseRecoveryService service;

    @BeforeEach
    void prepare() {
        jdbc.execute("DROP TABLE IF EXISTS task_execution");
        jdbc.execute("DROP TABLE IF EXISTS task_instance");
        jdbc.execute("CREATE TABLE task_instance (id BIGINT PRIMARY KEY, status VARCHAR(20) NOT NULL, attempt_count INT NOT NULL, max_retries INT NOT NULL, lease_owner VARCHAR(128), lease_until TIMESTAMP(3), lease_version BIGINT NOT NULL)");
        jdbc.execute("CREATE TABLE task_execution (id BIGINT AUTO_INCREMENT PRIMARY KEY, instance_id BIGINT NOT NULL, attempt_no INT NOT NULL, worker_id VARCHAR(128), lease_version BIGINT NOT NULL, status VARCHAR(20) NOT NULL, end_time TIMESTAMP(3), error_message VARCHAR(2000))");
    }

    @Test
    void expiredAttemptWithRetriesEntersRetryWait() {
        insertInstance(100, 1, 2, -60);
        insertExecution(100, 1);
        assertEquals(1, service.recoverBatch(10));
        assertEquals("RETRY_WAIT", status(100));
        assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM task_execution WHERE instance_id = 100", String.class));
        assertNotNull(jdbc.queryForObject("SELECT end_time FROM task_execution WHERE instance_id = 100", java.sql.Timestamp.class));
        assertEquals(0, service.recoverBatch(10));
    }

    @Test
    void exhaustedAttemptBecomesFinalFailure() {
        insertInstance(101, 3, 2, -60);
        insertExecution(101, 3);
        assertEquals(1, service.recoverBatch(10));
        assertEquals("FAILED", status(101));
        assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM task_execution WHERE instance_id = 101", String.class));
    }

    @Test
    void missingExecutionRollsBackInstanceUpdate() {
        insertInstance(102, 1, 2, -60);
        assertThrows(IllegalStateException.class, () -> service.recoverBatch(10));
        assertEquals("RUNNING", status(102));
        assertEquals("worker-A", jdbc.queryForObject("SELECT lease_owner FROM task_instance WHERE id = 102", String.class));
    }

    @Test
    void unexpiredLeaseIsNotRecovered() {
        insertInstance(103, 1, 2, 600);
        insertExecution(103, 1);
        assertEquals(0, service.recoverBatch(10));
        assertEquals("RUNNING", status(103));
    }

    private String status(long id) {
        return jdbc.queryForObject("SELECT status FROM task_instance WHERE id = ?", String.class, id);
    }
    private void insertInstance(long id, int attempts, int retries, int seconds) {
        jdbc.update("INSERT INTO task_instance (id,status,attempt_count,max_retries,lease_owner,lease_until,lease_version) VALUES (?, 'RUNNING', ?, ?, 'worker-A', TIMESTAMPADD(SECOND, ?, UTC_TIMESTAMP(3)), 1)", id, attempts, retries, seconds);
    }
    private void insertExecution(long id, int attempt) {
        jdbc.update("INSERT INTO task_execution (instance_id,attempt_no,worker_id,lease_version,status) VALUES (?, ?, 'worker-A', 1, 'RUNNING')", id, attempt);
    }

    @Configuration
    @Import(WorkerResultTransactionTest.TestConfig.class)
    static class TestConfig {
        @Bean LeaseRecoveryService leaseRecoveryService(TaskInstanceMapper instances, TaskExecutionMapper executions) {
            return new LeaseRecoveryService(instances, executions);
        }
    }
}
