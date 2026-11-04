package com.taskflow.task.service;

import com.taskflow.task.dto.WorkerTaskContext;
import com.taskflow.task.handler.TaskHandler;
import com.taskflow.task.handler.TaskHandlerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TaskWorker {

    private final WorkerClaimService claimService;
    private final TaskHandlerRegistry handlerRegistry;
    private final WorkerResultService resultService;
    private final WorkerHeartbeatService heartbeatService;

    public ExecutionOutcome execute(Long instanceId, String workerId) {
        WorkerTaskContext context = claimService.claim(instanceId, workerId);
        if (context == null) {
            return ExecutionOutcome.NOT_CLAIMED;
        }

        try (WorkerHeartbeatService.Heartbeat heartbeat = heartbeatService.start(context)) {
            String resultJson;
            try {
                TaskHandler handler = handlerRegistry.getHandler(context.getHandlerType());
                resultJson = handler.execute(context);
            } catch (RuntimeException ex) {
                if (heartbeat.isLeaseLost()) {
                    throw ex;
                }
                String message = ex.getMessage();
                if (message == null || message.isBlank()) {
                    message = ex.getClass().getSimpleName();
                }
                int length = message.codePointCount(0, message.length());
                if (length > 2000) {
                    message = message.substring(0, message.offsetByCodePoints(0, 2000));
                }
                if (RetryPolicy.canRetry(context.getAttemptNo(), context.getMaxRetries())) {
                    resultService.markRetryWait(context, message);
                    return ExecutionOutcome.RETRY_WAIT;
                }
                resultService.markFinalFailure(context, message);
                return ExecutionOutcome.FAILED;
            }

            if (heartbeat.isLeaseLost()) {
                throw new IllegalStateException("Task lease may no longer be valid: " + instanceId);
            }
            resultService.markSuccess(context, resultJson);
            return ExecutionOutcome.SUCCEEDED;
        }
    }

    public enum ExecutionOutcome {
        NOT_CLAIMED,
        RETRY_WAIT,
        SUCCEEDED,
        FAILED
    }
}
