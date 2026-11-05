package com.taskflow.task.scheduler;

import com.taskflow.task.service.LeaseRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "taskflow.scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class LeaseRecoveryJob {

    private final LeaseRecoveryService recoveryService;

    @Scheduled(
            fixedDelayString =
                    "${taskflow.scheduler.poll-delay-ms:5000}",
            initialDelay = 10000
    )
    public void recoverExpiredInstances() {
        try {
            int recovered = recoveryService.recoverBatch(10);

            if (recovered > 0) {
                log.info(
                        "Recovered {} expired task instances",
                        recovered
                );
            }
        } catch (Exception ex) {
            log.error("Lease recovery batch failed", ex);
        }
    }
}