package com.taskflow.task.service;

import com.taskflow.task.mapper.OutboxMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboxStateServiceTest {

    @Test
    void markSentUsesPublishedOutboxTransition() {
        List<Object> call = new ArrayList<>();
        OutboxMapper mapper = new OutboxMapper() {
            @Override
            public int insert(com.taskflow.task.entity.OutboxEvent event) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<com.taskflow.task.entity.OutboxEvent> selectClaimable(
                    int limit) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int claim(Long id, String token, int leaseSeconds) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int markPublished(Long id, String token) {
                call.add(id);
                call.add(token);
                return 1;
            }

            @Override
            public int scheduleRetry(Long id, String token, long delaySeconds) {
                throw new UnsupportedOperationException();
            }
        };

        OutboxStateService service = new OutboxStateService(mapper);

        assertTrue(service.markSent(7L, "claim-token"));
        assertEquals(List.of(7L, "claim-token"), call);
    }
}
