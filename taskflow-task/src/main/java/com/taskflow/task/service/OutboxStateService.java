
package com.taskflow.task.service;

import com.taskflow.task.mapper.OutboxMapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OutboxStateService {

    private final OutboxMapper outboxMapper;

    public boolean markSent(Long id, String token) {
        return outboxMapper.markPublished(id, token) == 1;
    }

    public boolean scheduleRetry(
            Long id,
            String token,
            int retryCount) {

        // 指数退避，最长 1 小时
        long delaySeconds = Math.min(
                3600L,
                5L * (1L << Math.min(retryCount, 10))
        );

        return outboxMapper.scheduleRetry(
                id, token, delaySeconds
        ) == 1;
    }
}
