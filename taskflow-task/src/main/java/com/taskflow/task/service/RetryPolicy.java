package com.taskflow.task.service;

public final class RetryPolicy {

    private RetryPolicy() {
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