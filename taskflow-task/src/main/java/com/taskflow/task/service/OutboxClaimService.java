
package com.taskflow.task.service;

import com.taskflow.task.entity.OutboxEvent;
import com.taskflow.task.mapper.OutboxMapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxClaimService {

    private final OutboxMapper outboxMapper;

    @Transactional
    public List<OutboxEvent> claimBatch() {

        List<OutboxEvent> events =
                outboxMapper.selectClaimable(5);

        for (OutboxEvent event : events) {

            String token = UUID.randomUUID().toString();

            int rows = outboxMapper.claim(
                    event.getId(),
                    token,
                    120
            );

            if (rows != 1) {
                throw new IllegalStateException(
                        "Outbox claim failed");
            }

            event.setStatus("PUBLISHING");
            event.setClaimToken(token);
        }

        return events;
    }
}
