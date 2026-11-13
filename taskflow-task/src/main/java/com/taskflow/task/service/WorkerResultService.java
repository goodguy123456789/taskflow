package com.taskflow.task.service;

import org.springframework.beans.factory.annotation.Value;
import com.taskflow.task.dto.WorkerTaskContext;
import com.taskflow.task.mapper.TaskExecutionMapper;
import com.taskflow.task.mapper.TaskInstanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkerResultService {

    private final TaskInstanceMapper instanceMapper;
    private final TaskExecutionMapper executionMapper;

    @Value("${taskflow.worker.retry-initial-delay-seconds:30}")
    private int retryInitialDelaySeconds;

    @Value("${taskflow.worker.retry-max-delay-seconds:300}")
    private int retryMaxDelaySeconds;

    @Value("${taskflow.worker.retry-jitter-enabled:true}")
    private boolean retryJitterEnabled;

    @Transactional
    public void markSuccess(
            WorkerTaskContext context,
            String resultJson
    ) {
        int instanceRows = instanceMapper.markSuccess(
                context.getInstanceId(),
                context.getWorkerId(),
                context.getLeaseVersion()
        );

        if (instanceRows != 1) {
            throw new IllegalStateException(
                    "Task lease is no longer valid: "
                            + context.getInstanceId()
            );
        }

        int executionRows = executionMapper.markSuccess(
                context.getInstanceId(),
                context.getAttemptNo(),
                context.getWorkerId(),
                context.getLeaseVersion(),
                resultJson
        );

        if (executionRows != 1) {
            throw new IllegalStateException(
                    "Failed to update execution attempt: "
                            + context.getInstanceId()
            );
        }
    }
    @Transactional
    public void markFinalFailure(WorkerTaskContext context, String errorMessage) {
        int instanceRows = instanceMapper.markFailed(
                context.getInstanceId(), context.getWorkerId(), context.getLeaseVersion());
        if (instanceRows != 1) {
            throw new IllegalStateException("Task lease is no longer valid: " + context.getInstanceId());
        }

        int executionRows = executionMapper.markFailed(
                context.getInstanceId(), context.getAttemptNo(), context.getWorkerId(),
                context.getLeaseVersion(), errorMessage);
        if (executionRows != 1) {
            throw new IllegalStateException("Failed to update execution attempt: " + context.getInstanceId());
        }
    }

    @Transactional
    public void markRetryWait(WorkerTaskContext context, String errorMessage) {
        // 每次失败只计算一次抖动，后续扫描使用保存的到期时间。
        long retryDelaySeconds = RetryBackoff.delaySeconds(
                context.getAttemptNo(),
                retryInitialDelaySeconds,
                retryMaxDelaySeconds,
                retryJitterEnabled
        );

        int instanceRows = instanceMapper.markRetryWait(
                context.getInstanceId(), context.getWorkerId(), context.getLeaseVersion());
        if (instanceRows != 1) {
            throw new IllegalStateException("Task lease is no longer valid: " + context.getInstanceId());
        }

        // 与实例状态更新共用事务，任一更新失败时一起回滚。
        int executionRows = executionMapper.markFailedForRetry(
                context.getInstanceId(), context.getAttemptNo(), context.getWorkerId(),
                context.getLeaseVersion(), errorMessage, retryDelaySeconds);
        if (executionRows != 1) {
            throw new IllegalStateException("Failed to update execution attempt: " + context.getInstanceId());
        }
    }
}