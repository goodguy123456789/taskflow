package com.taskflow.task.service;

import com.taskflow.task.mapper.OutboxMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxStateServiceTest {

    @Test
    void markSentUsesPublishedOutboxTransition() {
        OutboxMapper mapper = mock(OutboxMapper.class);
        when(mapper.markPublished(7L, "claim-token")).thenReturn(1);

        OutboxStateService service = new OutboxStateService(mapper);

        assertTrue(service.markSent(7L, "claim-token"));
        verify(mapper).markPublished(7L, "claim-token");
    }
}