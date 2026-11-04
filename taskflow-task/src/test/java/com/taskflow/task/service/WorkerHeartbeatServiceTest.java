package com.taskflow.task.service;

import com.taskflow.task.dto.WorkerTaskContext;
import com.taskflow.task.mapper.TaskInstanceMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkerHeartbeatServiceTest {

    @Test
    void renewsLeaseUsingCurrentOwnerAndVersion() throws Exception {
        TaskInstanceMapper mapper = mock(TaskInstanceMapper.class);
        CountDownLatch called = new CountDownLatch(1);
        when(mapper.renewLease(100L, "worker-A", 1L, 3)).thenAnswer(invocation -> {
            called.countDown();
            return 1;
        });
        WorkerHeartbeatService service = createService(mapper);
        try (WorkerHeartbeatService.Heartbeat heartbeat = service.start(context())) {
            assertTrue(called.await(5, TimeUnit.SECONDS));
            assertFalse(heartbeat.isLeaseLost());
            verify(mapper, atLeastOnce()).renewLease(100L, "worker-A", 1L, 3);
        } finally {
            service.shutdown();
        }
    }

    @Test
    void marksLeaseLostWhenRenewalFails() throws Exception {
        TaskInstanceMapper mapper = mock(TaskInstanceMapper.class);
        when(mapper.renewLease(100L, "worker-A", 1L, 3)).thenReturn(0);
        WorkerHeartbeatService service = createService(mapper);
        try (WorkerHeartbeatService.Heartbeat heartbeat = service.start(context())) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!heartbeat.isLeaseLost() && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(heartbeat.isLeaseLost());
        } finally {
            service.shutdown();
        }
    }

    @Test
    void marksLeaseLostWhenDatabaseCallThrows() throws Exception {
        TaskInstanceMapper mapper = mock(TaskInstanceMapper.class);
        when(mapper.renewLease(100L, "worker-A", 1L, 3)).thenThrow(new IllegalStateException("database unavailable"));
        WorkerHeartbeatService service = createService(mapper);
        try (WorkerHeartbeatService.Heartbeat heartbeat = service.start(context())) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!heartbeat.isLeaseLost() && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(heartbeat.isLeaseLost());
        } finally {
            service.shutdown();
        }
    }

    private static WorkerHeartbeatService createService(TaskInstanceMapper mapper) {
        WorkerHeartbeatService service = new WorkerHeartbeatService(mapper);
        ReflectionTestUtils.setField(service, "leaseSeconds", 3);
        ReflectionTestUtils.setField(service, "heartbeatSeconds", 1);
        return service;
    }

    private static WorkerTaskContext context() {
        WorkerTaskContext context = new WorkerTaskContext();
        context.setInstanceId(100L);
        context.setWorkerId("worker-A");
        context.setLeaseVersion(1L);
        return context;
    }
}
