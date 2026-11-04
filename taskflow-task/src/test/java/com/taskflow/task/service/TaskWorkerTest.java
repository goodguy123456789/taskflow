package com.taskflow.task.service;

import com.taskflow.task.dto.WorkerTaskContext;
import com.taskflow.task.handler.TaskHandler;
import com.taskflow.task.handler.TaskHandlerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskWorkerTest {
    private WorkerClaimService claimService;
    private TaskHandlerRegistry handlerRegistry;
    private WorkerResultService resultService;
    private WorkerHeartbeatService heartbeatService;
    private WorkerHeartbeatService.Heartbeat heartbeat;
    private TaskHandler handler;
    private TaskWorker worker;
    private WorkerTaskContext context;

    @BeforeEach
    void setUp() {
        claimService = mock(WorkerClaimService.class);
        handlerRegistry = mock(TaskHandlerRegistry.class);
        resultService = mock(WorkerResultService.class);
        heartbeatService = mock(WorkerHeartbeatService.class);
        heartbeat = mock(WorkerHeartbeatService.Heartbeat.class);
        handler = mock(TaskHandler.class);
        worker = new TaskWorker(claimService, handlerRegistry, resultService, heartbeatService);
        context = new WorkerTaskContext();
        context.setInstanceId(100L);
        context.setHandlerType("PRINT_LOG");
        context.setAttemptNo(1);
        context.setMaxRetries(0);
        when(claimService.claim(100L, "worker-A")).thenReturn(context);
        when(heartbeatService.start(context)).thenReturn(heartbeat);
        when(handlerRegistry.getHandler("PRINT_LOG")).thenReturn(handler);
    }

    @Test
    void executesAndSavesSuccessWhileHeartbeatIsActive() {
        when(handler.execute(context)).thenReturn("{}");
        assertEquals(TaskWorker.ExecutionOutcome.SUCCEEDED, worker.execute(100L, "worker-A"));
        InOrder order = inOrder(claimService, heartbeatService, handler, resultService, heartbeat);
        order.verify(claimService).claim(100L, "worker-A");
        order.verify(heartbeatService).start(context);
        order.verify(handler).execute(context);
        order.verify(resultService).markSuccess(context, "{}");
        order.verify(heartbeat).close();
    }

    @Test
    void doesNotExecuteWhenNotClaimed() {
        when(claimService.claim(100L, "worker-B")).thenReturn(null);
        assertEquals(TaskWorker.ExecutionOutcome.NOT_CLAIMED, worker.execute(100L, "worker-B"));
        verifyNoInteractions(heartbeatService, handlerRegistry, handler, resultService);
    }

    @Test
    void savesFinalFailureWhenNoRetriesRemain() {
        when(handler.execute(context)).thenThrow(new IllegalStateException("执行失败"));
        assertEquals(TaskWorker.ExecutionOutcome.FAILED, worker.execute(100L, "worker-A"));
        verify(resultService).markFinalFailure(context, "执行失败");
        verify(heartbeat).close();
    }

    @Test
    void savesRetryableHandlerFailureWithoutMarkingFinal() {
        context.setMaxRetries(2);
        IllegalStateException failure = new IllegalStateException("暂时失败");
        when(handler.execute(context)).thenThrow(failure);
        assertEquals(TaskWorker.ExecutionOutcome.RETRY_WAIT, worker.execute(100L, "worker-A"));
        verify(resultService).markRetryWait(context, "暂时失败");
        verify(resultService, never()).markFinalFailure(any(), any());
        verify(heartbeat).close();
    }

    @Test
    void doesNotSaveWhenHeartbeatReportsLeaseLost() {
        when(handler.execute(context)).thenReturn("{}");
        when(heartbeat.isLeaseLost()).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> worker.execute(100L, "worker-A"));
        verifyNoInteractions(resultService);
        verify(heartbeat).close();
    }

    @Test
    void propagatesResultSaveFailureWithoutMarkingBusinessFailure() {
        when(handler.execute(context)).thenReturn("{}");
        IllegalStateException failure = new IllegalStateException("结果保存失败");
        doThrow(failure).when(resultService).markSuccess(context, "{}");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> worker.execute(100L, "worker-A")));
        verify(resultService, never()).markFinalFailure(any(), any());
        verify(heartbeat).close();
    }
}
