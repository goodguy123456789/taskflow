package com.taskflow.task.service;

import com.taskflow.task.dto.WorkerTaskContext;
import com.taskflow.task.mapper.TaskInstanceMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class WorkerHeartbeatService {

    private final TaskInstanceMapper instanceMapper;

    private final ScheduledExecutorService executor =
            Executors.newScheduledThreadPool(2);

    @Value("${taskflow.worker.lease-seconds:60}")
    private int leaseSeconds;

    @Value("${taskflow.worker.heartbeat-seconds:20}")
    private int heartbeatSeconds;

    public WorkerHeartbeatService(
            TaskInstanceMapper instanceMapper
    ) {
        this.instanceMapper = instanceMapper;
    }

    public Heartbeat start(WorkerTaskContext context) {
        if (leaseSeconds <= 0
                || heartbeatSeconds <= 0
                || heartbeatSeconds >= leaseSeconds) {
            throw new IllegalStateException(
                    "Heartbeat interval must be positive "
                            + "and shorter than the lease"
            );
        }

        AtomicBoolean leaseLost = new AtomicBoolean(false);

        ScheduledFuture<?> future =
                executor.scheduleWithFixedDelay(
                        () -> renew(context, leaseLost),
                        heartbeatSeconds,
                        heartbeatSeconds,
                        TimeUnit.SECONDS
                );

        return new Heartbeat(future, leaseLost);
    }

    private void renew(
            WorkerTaskContext context,
            AtomicBoolean leaseLost
    ) {
        if (leaseLost.get()) {
            return;
        }

        try {
            int rows = instanceMapper.renewLease(
                    context.getInstanceId(),
                    context.getWorkerId(),
                    context.getLeaseVersion(),
                    leaseSeconds
            );

            if (rows != 1) {
                leaseLost.set(true);

                log.warn(
                        "Worker lease lost: instanceId={}, "
                                + "workerId={}, leaseVersion={}",
                        context.getInstanceId(),
                        context.getWorkerId(),
                        context.getLeaseVersion()
                );
            }
        } catch (Exception ex) {
            // 数据库发生异常时，无法确认租约是否仍有效。
            leaseLost.set(true);

            log.error(
                    "Worker lease renewal failed: instanceId={}",
                    context.getInstanceId(),
                    ex
            );
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    public static final class Heartbeat implements AutoCloseable {

        private final ScheduledFuture<?> future;
        private final AtomicBoolean leaseLost;

        private Heartbeat(
                ScheduledFuture<?> future,
                AtomicBoolean leaseLost
        ) {
            this.future = future;
            this.leaseLost = leaseLost;
        }

        public boolean isLeaseLost() {
            return leaseLost.get();
        }

        @Override
        public void close() {
            future.cancel(false);
        }
    }
}