package com.taskflow.task.service;

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
}