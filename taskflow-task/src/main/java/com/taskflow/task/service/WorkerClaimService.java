package com.taskflow.task.service;

import com.taskflow.task.dto.WorkerTaskContext;
import com.taskflow.task.mapper.TaskInstanceMapper;
import com.taskflow.task.mapper.TaskExecutionMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkerClaimService {

    private final TaskInstanceMapper instanceMapper;
    private final TaskExecutionMapper executionMapper;

    @Value("${taskflow.worker.lease-seconds:60}")
    private int leaseSeconds;

    @Transactional
    public WorkerTaskContext claim(
            Long instanceId,
            String workerId) {

        // 1. 原子抢占
        int rows = instanceMapper.claimQueued(
                instanceId,
                workerId,
                leaseSeconds
        );

        // 2. 抢占失败，说明当前不能获得执行权
        if (rows == 0) {
            return null;
        }

        // 3. 获取任务执行上下文
        WorkerTaskContext context =
                instanceMapper.findWorkerContext(instanceId);

        if (context == null) {
            throw new IllegalStateException(
                    "Task context not found: " + instanceId
            );
        }

        // 4. retryCount 由 attempt_count 计算，并非数据库字段
        // 第一次执行 attempt_no = 1
        context.setAttemptNo(
                context.getRetryCount() + 1
        );

        // 5. 创建本次执行记录
        int inserted = executionMapper.insertRunningAttempt(
                instanceId,
                context.getAttemptNo(),
                workerId,
                context.getLeaseVersion()
        );

        if (inserted != 1) {
            throw new IllegalStateException(
                    "Failed to create task execution"
            );
        }

        return context;
    }
}