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

    public ExecutionOutcome execute(
            Long instanceId,
            String workerId
    ) {
        // 1. 领取实例。该方法内部有一个短事务。
        WorkerTaskContext context =
                claimService.claim(instanceId, workerId);

        if (context == null) {
            return ExecutionOutcome.NOT_CLAIMED;
        }

        // 2. 领取事务结束后，再执行具体任务。
        TaskHandler handler =
                handlerRegistry.getHandler(
                        context.getHandlerType()
                );

        String resultJson = handler.execute(context);

        // 3. 保存结果。该方法内部有另一个短事务。
        resultService.markSuccess(context, resultJson);

        return ExecutionOutcome.SUCCEEDED;
    }

    public enum ExecutionOutcome {
        NOT_CLAIMED,
        SUCCEEDED
    }
}
