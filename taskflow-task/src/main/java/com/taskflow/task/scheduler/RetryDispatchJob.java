package com.taskflow.task.scheduler;

import com.taskflow.task.service.RetryDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "taskflow.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RetryDispatchJob {
    private final RetryDispatchService retryDispatchService;

    @Value("${taskflow.worker.retry-delay-seconds:30}")
    private int retryDelaySeconds;

    @Scheduled(fixedDelayString = "${taskflow.scheduler.poll-delay-ms:5000}", initialDelay = 10000)
    public void dispatchRetries() {
        try {
            int count = retryDispatchService.dispatchDue(retryDelaySeconds, 10);
            if (count > 0) {
                log.info("Scheduled {} retry instances", count);
            }
        } catch (Exception ex) {
            log.error("Retry dispatch failed", ex);
        }
    }
}
