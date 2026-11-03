package com.taskflow.task.service;

import com.taskflow.task.dto.WorkerTaskContext;
import com.taskflow.task.mapper.TaskExecutionMapper;
import com.taskflow.task.mapper.TaskInstanceMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(WorkerResultTransactionTest.TestConfig.class)
public class WorkerResultTransactionTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private WorkerResultService service;

    public static Timestamp utcTimestamp(int precision) {
        return Timestamp.from(Instant.now());
    }

    @BeforeEach
    void prepareDatabase() {
        jdbc.execute("DROP TABLE IF EXISTS task_execution");
        jdbc.execute("DROP TABLE IF EXISTS task_instance");
        jdbc.execute("CREATE TABLE task_instance (id BIGINT PRIMARY KEY, status VARCHAR(20) NOT NULL, lease_owner VARCHAR(128), lease_until TIMESTAMP(3), lease_version BIGINT NOT NULL)");
        jdbc.execute("CREATE TABLE task_execution (id BIGINT AUTO_INCREMENT PRIMARY KEY, instance_id BIGINT NOT NULL, attempt_no INT NOT NULL, worker_id VARCHAR(128), lease_version BIGINT NOT NULL, status VARCHAR(20) NOT NULL, end_time TIMESTAMP(3), result_summary JSON)");
        jdbc.update("INSERT INTO task_instance VALUES (100, 'RUNNING', 'worker-A', DATEADD('MINUTE', 5, CURRENT_TIMESTAMP), 1)");
    }

    private WorkerTaskContext context() {
        WorkerTaskContext context = new WorkerTaskContext();
        context.setInstanceId(100L);
        context.setAttemptNo(1);
        context.setWorkerId("worker-A");
        context.setLeaseVersion(1L);
        return context;
    }

    @Test
    void rollsBackInstanceWhenExecutionRecordIsMissing() {
        assertThrows(IllegalStateException.class, () -> service.markSuccess(context(), "{\"message\":\"logged\"}"));
        assertEquals("RUNNING", jdbc.queryForObject("SELECT status FROM task_instance WHERE id = 100", String.class));
        assertEquals("worker-A", jdbc.queryForObject("SELECT lease_owner FROM task_instance WHERE id = 100", String.class));
    }

    @Test
    void completesBothRecordsForValidLease() {
        jdbc.update("INSERT INTO task_execution(instance_id, attempt_no, worker_id, lease_version, status) VALUES (100, 1, 'worker-A', 1, 'RUNNING')");
        service.markSuccess(context(), "{\"message\":\"logged\"}");
        assertEquals("SUCCESS", jdbc.queryForObject("SELECT status FROM task_instance WHERE id = 100", String.class));
        assertEquals("SUCCESS", jdbc.queryForObject("SELECT status FROM task_execution WHERE instance_id = 100", String.class));
        assertNull(jdbc.queryForObject("SELECT lease_owner FROM task_instance WHERE id = 100", String.class));
    }

    @Test
    void rejectsOldLeaseWithoutChangingEitherRecord() {
        jdbc.update("INSERT INTO task_execution(instance_id, attempt_no, worker_id, lease_version, status) VALUES (100, 1, 'worker-A', 1, 'RUNNING')");
        jdbc.update("UPDATE task_instance SET lease_version = 2 WHERE id = 100");
        assertThrows(IllegalStateException.class, () -> service.markSuccess(context(), "{}"));
        assertEquals("RUNNING", jdbc.queryForObject("SELECT status FROM task_instance WHERE id = 100", String.class));
        assertEquals("RUNNING", jdbc.queryForObject("SELECT status FROM task_execution WHERE instance_id = 100", String.class));
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {
        @Bean DataSource dataSource() {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:worker-result-tx;MODE=MySQL;DB_CLOSE_DELAY=-1");
            ds.setUser("sa");
            ds.setPassword("");
            return ds;
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) {
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            jdbc.execute("CREATE ALIAS IF NOT EXISTS UTC_TIMESTAMP FOR \"com.taskflow.task.service.WorkerResultTransactionTest.utcTimestamp\"");
            return jdbc;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(ds);
            factory.setMapperLocations(new ClassPathResource("mapper/TaskInstanceMapper.xml"), new ClassPathResource("mapper/TaskExecutionMapper.xml"));
            return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean TaskInstanceMapper taskInstanceMapper(SqlSessionTemplate template) { return template.getMapper(TaskInstanceMapper.class); }
        @Bean TaskExecutionMapper taskExecutionMapper(SqlSessionTemplate template) { return template.getMapper(TaskExecutionMapper.class); }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean WorkerResultService workerResultService(TaskInstanceMapper instances, TaskExecutionMapper executions) { return new WorkerResultService(instances, executions); }
    }
}
