package com.taskflow.task.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.task.service.TaskWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskInstanceReadyListenerTest {
    private TaskWorker worker;
    private TaskInstanceReadyListener listener;
    private final String valid = "{\"eventId\":\"event-1\",\"eventType\":\"TASK_INSTANCE_READY\",\"instanceId\":100,\"definitionId\":10,\"userId\":1,\"scheduledAt\":\"2026-11-05T00:00:00\"}";

    @BeforeEach
    void setUp() {
        worker = mock(TaskWorker.class);
        listener = new TaskInstanceReadyListener(new TaskInstanceReadyEventParser(new ObjectMapper()), worker);
    }

    @Test
    void parsesEventAndCallsWorkerWithStableIdentity() {
        when(worker.execute(eq(100L), startsWith("worker-"))).thenReturn(TaskWorker.ExecutionOutcome.SUCCEEDED);
        listener.handle(valid);
        listener.handle(valid);
        var identity = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(worker, times(2)).execute(eq(100L), identity.capture());
        assertEquals(identity.getAllValues().get(0), identity.getAllValues().get(1));
    }

    @Test
    void rejectsNotClaimedWithoutPretendingSuccess() {
        when(worker.execute(eq(100L), anyString())).thenReturn(TaskWorker.ExecutionOutcome.NOT_CLAIMED);
        assertThrows(IllegalStateException.class, () -> listener.handle(valid));
    }

    @Test
    void invalidEventNeverReachesWorker() {
        assertThrows(IllegalArgumentException.class, () -> listener.handle("{bad json}"));
        verifyNoInteractions(worker);
    }

    @Test
    void propagatesWorkerFailure() {
        IllegalStateException failure = new IllegalStateException("execution error");
        when(worker.execute(eq(100L), anyString())).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> listener.handle(valid)));
    }

    @Test
    void listenerDeclaresSafeDefaultStartupSetting() throws Exception {
        RabbitListener annotation = TaskInstanceReadyListener.class.getMethod("handle", String.class).getAnnotation(RabbitListener.class);
        assertEquals("${taskflow.worker.listener-enabled:false}", annotation.autoStartup());
        assertArrayEquals(new String[]{"task.instance.ready.q"}, annotation.queues());
    }
}
