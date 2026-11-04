package com.taskflow.task.service;

import com.taskflow.task.dto.WorkerTaskContext;
import com.taskflow.task.handler.TaskHandler;
import com.taskflow.task.handler.TaskHandlerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class TaskWorkerTest {
    private WorkerClaimService claimService;
    private TaskHandlerRegistry handlerRegistry;
    private WorkerResultService resultService;
    private TaskHandler handler;
    private TaskWorker worker;

    @BeforeEach
    void setUp() {
        claimService = mock(WorkerClaimService.class);
        handlerRegistry = mock(TaskHandlerRegistry.class);
        resultService = mock(WorkerResultService.class);
        handler = mock(TaskHandler.class);
        worker = new TaskWorker(claimService, handlerRegistry, resultService);
    }

    @Test
    void executesHandlerAndSavesSuccessInOrder() {
        WorkerTaskContext context = new WorkerTaskContext();
        context.setInstanceId(100L);
        context.setHandlerType("PRINT_LOG");
        String resultJson = "{\"message\":\"logged\"}";
        when(claimService.claim(100L, "worker-A")).thenReturn(context);
        when(handlerRegistry.getHandler("PRINT_LOG")).thenReturn(handler);
        when(handler.execute(context)).thenReturn(resultJson);

        assertEquals(TaskWorker.ExecutionOutcome.SUCCEEDED, worker.execute(100L, "worker-A"));

        InOrder order = inOrder(claimService, handlerRegistry, handler, resultService);
        order.verify(claimService).claim(100L, "worker-A");
        order.verify(handlerRegistry).getHandler("PRINT_LOG");
        order.verify(handler).execute(context);
        order.verify(resultService).markSuccess(context, resultJson);
    }

    @Test
    void doesNotExecuteWhenInstanceWasNotClaimed() {
        when(claimService.claim(100L, "worker-B")).thenReturn(null);
        assertEquals(TaskWorker.ExecutionOutcome.NOT_CLAIMED, worker.execute(100L, "worker-B"));
        verifyNoInteractions(handlerRegistry, handler, resultService);
    }

    @Test
    void propagatesHandlerFailureWithoutSavingSuccess() {
        WorkerTaskContext context = new WorkerTaskContext();
        context.setInstanceId(100L);
        context.setHandlerType("PRINT_LOG");
        when(claimService.claim(100L, "worker-A")).thenReturn(context);
        when(handlerRegistry.getHandler("PRINT_LOG")).thenReturn(handler);
        IllegalStateException failure = new IllegalStateException("执行失败");
        when(handler.execute(context)).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class, () -> worker.execute(100L, "worker-A")));
        verifyNoInteractions(resultService);
    }

    @Test
    void propagatesFailureToSaveResult() {
        WorkerTaskContext context = new WorkerTaskContext();
        context.setInstanceId(100L);
        context.setHandlerType("PRINT_LOG");
        when(claimService.claim(100L, "worker-A")).thenReturn(context);
        when(handlerRegistry.getHandler("PRINT_LOG")).thenReturn(handler);
        when(handler.execute(context)).thenReturn("{}");
        IllegalStateException failure = new IllegalStateException("租约失效");
        doThrow(failure).when(resultService).markSuccess(context, "{}");

        assertSame(failure, assertThrows(IllegalStateException.class, () -> worker.execute(100L, "worker-A")));
    }
}
