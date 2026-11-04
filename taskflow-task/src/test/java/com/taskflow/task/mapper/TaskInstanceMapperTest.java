package com.taskflow.task.mapper;

import com.taskflow.task.dto.WorkerTaskContext;
import com.taskflow.task.service.WorkerClaimService;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.Timestamp;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TaskInstanceMapperTest {

    public static Timestamp utcTimestamp(int precision) {
        return Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    void workerClaimsPendingInstanceOnceAndPersistsExecutionAttempt() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:worker-claim;MODE=MySQL;DB_CLOSE_DELAY=-1");
        createSchema(dataSource);

        Configuration configuration = new Configuration();
        configuration.setEnvironment(new org.apache.ibatis.mapping.Environment(
                "test", new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(), dataSource));
        configuration.addMapper(TaskInstanceMapper.class);
        configuration.addMapper(TaskExecutionMapper.class);
        try (InputStream taskMapper = Resources.getResourceAsStream("mapper/TaskInstanceMapper.xml")) {
            new XMLMapperBuilder(taskMapper, configuration,
                    "mapper/TaskInstanceMapper.xml", configuration.getSqlFragments()).parse();
        }
        try (InputStream executionMapper = Resources.getResourceAsStream("mapper/TaskExecutionMapper.xml")) {
            new XMLMapperBuilder(executionMapper, configuration,
                    "mapper/TaskExecutionMapper.xml", configuration.getSqlFragments()).parse();
        }
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);

        try (SqlSession session = factory.openSession(false)) {
            TaskInstanceMapper instanceMapper = session.getMapper(TaskInstanceMapper.class);
            TaskExecutionMapper executionMapper = session.getMapper(TaskExecutionMapper.class);
            WorkerClaimService worker = new WorkerClaimService(instanceMapper, executionMapper);
            ReflectionTestUtils.setField(worker, "leaseSeconds", 30);

            Long instanceId = insertPendingInstance(session.getConnection());
            WorkerTaskContext context = worker.claim(instanceId, "worker-a");

            assertEquals(instanceId, context.getInstanceId());
            assertEquals(0, context.getRetryCount());
            assertEquals(1, context.getAttemptNo());
            assertEquals(3, context.getMaxRetries());
            assertEquals(1L, context.getLeaseVersion());
            assertEquals("worker-a", context.getWorkerId());
            assertEquals("DEMO", context.getHandlerType());
            assertEquals("{}", context.getPayload());
            assertNull(worker.claim(instanceId, "worker-b"));

            session.commit();
            try (Statement statement = session.getConnection().createStatement();
                 var result = statement.executeQuery(
                         "SELECT status, attempt_count, lease_owner, lease_version "
                                 + "FROM task_instance WHERE id = " + instanceId)) {
                result.next();
                assertEquals("RUNNING", result.getString("status"));
                assertEquals(1, result.getInt("attempt_count"));
                assertEquals("worker-a", result.getString("lease_owner"));
                assertEquals(1L, result.getLong("lease_version"));
            }
            try (Statement statement = session.getConnection().createStatement();
                 var result = statement.executeQuery(
                         "SELECT COUNT(*) FROM task_execution WHERE instance_id = " + instanceId)) {
                result.next();
                assertEquals(1, result.getInt(1));
            }
        }
    }

    @Test
    void retryWaitingInstanceIsNotClaimedUntilItIsQueuedAgain() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:retry-wait-claim;MODE=MySQL;DB_CLOSE_DELAY=-1");
        createSchema(dataSource);

        Configuration configuration = new Configuration();
        configuration.setEnvironment(new org.apache.ibatis.mapping.Environment(
                "test", new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(), dataSource));
        configuration.addMapper(TaskInstanceMapper.class);
        try (InputStream xml = Resources.getResourceAsStream("mapper/TaskInstanceMapper.xml")) {
            new XMLMapperBuilder(xml, configuration, "mapper/TaskInstanceMapper.xml", configuration.getSqlFragments()).parse();
        }
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
        try (SqlSession session = factory.openSession(false)) {
            Long instanceId = insertPendingInstance(session.getConnection());
            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE task_instance SET status = 'RETRY_WAIT' WHERE id = " + instanceId);
            }
            TaskInstanceMapper mapper = session.getMapper(TaskInstanceMapper.class);
            assertEquals(0, mapper.claimQueued(instanceId, "worker-a", 30));
            try (Statement statement = session.getConnection().createStatement()) {
                try (var result = statement.executeQuery("SELECT status, attempt_count FROM task_instance WHERE id = " + instanceId)) {
                    result.next();
                    assertEquals("RETRY_WAIT", result.getString("status"));
                    assertEquals(0, result.getInt("attempt_count"));
                }
                statement.executeUpdate("UPDATE task_instance SET status = 'QUEUED' WHERE id = " + instanceId);
            }
            assertEquals(1, mapper.claimQueued(instanceId, "worker-a", 30));
        }
    }
    @Test
    void selectsOnlyExpiredRunningInstancesInExpiryOrderAndRespectsLimit() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:expired-lease-query;MODE=MySQL;DB_CLOSE_DELAY=-1");
        createSchema(dataSource);
        Configuration configuration = new Configuration();
        configuration.setEnvironment(new org.apache.ibatis.mapping.Environment(
                "test", new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(), dataSource));
        configuration.addMapper(TaskInstanceMapper.class);
        try (InputStream xml = Resources.getResourceAsStream("mapper/TaskInstanceMapper.xml")) {
            new XMLMapperBuilder(xml, configuration, "mapper/TaskInstanceMapper.xml", configuration.getSqlFragments()).parse();
        }
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
        try (SqlSession session = factory.openSession(false)) {
            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("INSERT INTO task_instance (definition_id, user_id, scheduled_at, status, handler_type, lease_owner, lease_until, lease_version, attempt_count, max_retries) VALUES "
                        + "(1, 1, UTC_TIMESTAMP(3), 'RUNNING', 'DEMO', 'old-worker', TIMESTAMPADD(SECOND, -120, UTC_TIMESTAMP(3)), 3, 2, 4), "
                        + "(2, 1, UTC_TIMESTAMP(3), 'RUNNING', 'DEMO', 'new-worker', TIMESTAMPADD(SECOND, -60, UTC_TIMESTAMP(3)), 1, 1, 2), "
                        + "(3, 1, UTC_TIMESTAMP(3), 'RUNNING', 'DEMO', 'active-worker', TIMESTAMPADD(SECOND, 60, UTC_TIMESTAMP(3)), 1, 1, 2), "
                        + "(4, 1, UTC_TIMESTAMP(3), 'SUCCESS', 'DEMO', 'done-worker', TIMESTAMPADD(SECOND, -300, UTC_TIMESTAMP(3)), 1, 1, 2)");
            }
            TaskInstanceMapper mapper = session.getMapper(TaskInstanceMapper.class);
            var selected = mapper.selectExpiredForUpdate(1);
            assertEquals(1, selected.size());
            assertEquals("old-worker", selected.get(0).getLeaseOwner());
            assertEquals(3L, selected.get(0).getLeaseVersion());
            assertEquals(2, selected.get(0).getAttemptCount());
            assertEquals(4, selected.get(0).getMaxRetries());
            session.rollback();
            assertEquals(2, mapper.selectExpiredForUpdate(10).size());
        }
    }
    private static Long insertPendingInstance(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO task_instance
                        (definition_id, user_id, scheduled_at, status, handler_type, payload, max_retries)
                    VALUES (7, 1, TIMESTAMP '2026-10-26 01:00:00', 'PENDING', 'DEMO', JSON_OBJECT(), 3)
                    """);
            try (var result = statement.executeQuery("SELECT MAX(id) FROM task_instance")) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static void createSchema(JdbcDataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ALIAS UTC_TIMESTAMP FOR "
                    + "\"com.taskflow.task.mapper.TaskInstanceMapperTest.utcTimestamp\"");
            statement.execute("""
                    CREATE TABLE task_instance (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        definition_id BIGINT NOT NULL,
                        user_id BIGINT NOT NULL,
                        scheduled_at TIMESTAMP(3) NOT NULL,
                        status VARCHAR(20) NOT NULL,
                        handler_type VARCHAR(64) NOT NULL,
                        payload JSON,
                        max_retries INT NOT NULL,
                        attempt_count INT NOT NULL DEFAULT 0,
                        lease_owner VARCHAR(128),
                        lease_until TIMESTAMP(3),
                        lease_version BIGINT NOT NULL DEFAULT 0
                    )
                    """);
            statement.execute("""
                    CREATE TABLE task_execution (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        instance_id BIGINT NOT NULL,
                        attempt_no INT NOT NULL,
                        worker_id VARCHAR(128),
                        lease_version BIGINT NOT NULL,
                        status VARCHAR(20) NOT NULL,
                        start_time TIMESTAMP(3),
                        end_time TIMESTAMP(3),
                        error_message VARCHAR(2000),
                        result_summary JSON,
                        created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                        CONSTRAINT uk_instance_attempt UNIQUE (instance_id, attempt_no)
                    )
                    """);
        }
    }
}
