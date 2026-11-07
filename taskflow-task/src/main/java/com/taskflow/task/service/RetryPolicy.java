package com.taskflow.task.service;

public final class RetryPolicy {

    private RetryPolicy() {
    }

    /**
 * 根据失败的尝试次数，计算指数退避的等待上限。
 *
 * @param attemptNo  本次失败是第几次尝试，从 1 开始
 * @param baseSeconds 第一次失败后的基础延时
 * @param maxSeconds 允许的最大延时
 */
public static int calculateBackoffSeconds(
        int attemptNo,
        int baseSeconds,
        int maxSeconds
) {
    if (attemptNo < 1 || baseSeconds <= 0 || maxSeconds < baseSeconds) {
        throw new IllegalArgumentException("Invalid backoff parameters");
    }

    int delay = baseSeconds;

    for (int i = 1; i < attemptNo && delay < maxSeconds; i++) {
        delay = (int) Math.min((long) delay * 2, maxSeconds);
    }

    return delay;
}

    /**
     * 判断本次执行失败后，是否还允许重试。
     *
     * @param attemptNo 本次是第几次尝试，从 1 开始
     * @param maxRetries 首次执行之后最多允许的重试次数
     * @return true 表示仍有重试机会
     */
    public static boolean canRetry(
            int attemptNo,
            int maxRetries
    ) {
        if (attemptNo < 1) {
            throw new IllegalArgumentException(
                    "attemptNo must be greater than zero"
            );
        }

        if (maxRetries < 0) {
            throw new IllegalArgumentException(
                    "maxRetries must not be negative"
            );
        }

        return attemptNo <= maxRetries;
    }
}