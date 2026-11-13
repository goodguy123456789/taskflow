package com.taskflow.task.service;

import java.util.concurrent.ThreadLocalRandom;

public final class RetryBackoff {

    private RetryBackoff() {
    }

    public static long delaySeconds(
            int attemptNo,
            int initialDelaySeconds,
            int maxDelaySeconds,
            boolean jitterEnabled
    ) {
        if (attemptNo < 1) {
            throw new IllegalArgumentException("attemptNo must be at least 1");
        }
        if (initialDelaySeconds < 1 || maxDelaySeconds < initialDelaySeconds) {
            throw new IllegalArgumentException(
                    "Delay limits must satisfy 1 <= initialDelaySeconds <= maxDelaySeconds");
        }

        long delay = initialDelaySeconds;
        for (int attempt = 1; attempt < attemptNo && delay < maxDelaySeconds; attempt++) {
            delay = Math.min(delay * 2, maxDelaySeconds);
        }

        if (!jitterEnabled) {
            return delay;
        }

        long minDelay = delay - delay / 5;
        return ThreadLocalRandom.current().nextLong(minDelay, delay + 1);
    }
}