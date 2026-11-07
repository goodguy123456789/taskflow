package com.taskflow.task.service;

import com.taskflow.task.entity.TaskInstance;
import com.taskflow.task.mapper.TaskExecutionMapper;
import com.taskflow.task.mapper.TaskInstanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaseRecoveryService {

    private static final String EXPIRED_MESSAGE =
            "Worker lease expired before completion";

    private final TaskInstanceMapper instanceMapper;
    private final TaskExecutionMapper executionMapper;

    @Transactional
    public int recoverBatch(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException(
                    "Recovery batch size must be positive"
            );
        }

        List<TaskInstance> expiredInstances =
                instanceMapper.selectExpiredForUpdate(limit);

        for (TaskInstance instance : expiredInstances) {
            boolean canRetry = RetryPolicy.canRetry(
                    instance.getAttemptCount(),
                    instance.getMaxRetries()
            );

            String newStatus = canRetry
                    ? "RETRY_WAIT"
                    : "FAILED";

            int instanceRows = instanceMapper.recoverExpired(
                    instance.getId(),
                    instance.getLeaseOwner(),
                    instance.getLeaseVersion(),
                    newStatus
            );

            if (instanceRows != 1) {
                throw new IllegalStateException(
                        "Failed to recover expired instance: "
                                + instance.getId()
                );
            }

            int executionRows = executionMapper.markFailed(
                    instance.getId(),
                    instance.getAttemptCount(),
                    instance.getLeaseOwner(),
                    instance.getLeaseVersion(),
                    EXPIRED_MESSAGE
            );

            if (executionRows != 1) {
                throw new IllegalStateException(
                        "Failed to close expired execution: "
                                + instance.getId()
                );
            }
        }

        return expiredInstances.size();
    }
}