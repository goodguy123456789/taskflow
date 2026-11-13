package com.taskflow.task.scheduler;

import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public final class CronTriggerCalculator {

    private CronTriggerCalculator() {
    }

    public static ZonedDateTime nextAfter(String expression, ZonedDateTime reference) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("CRON expression is required");
        }
        if (reference == null) {
            throw new IllegalArgumentException("Reference time is required");
        }

        // 按参考时间的时区，寻找严格晚于它的触发点。
        CronExpression cron = CronExpression.parse(expression.strip());
        ZonedDateTime next = cron.next(reference);

        // 有些表达式格式合法，但没有实际的触发日期，例如 2 月 31 日。
        if (next == null) {
            throw new IllegalArgumentException("CRON expression has no next trigger");
        }
        return next;
    }

    public static LocalDateTime nextUtcAfter(
            String expression, LocalDateTime referenceUtc, ZoneId taskZone
    ) {
        if (referenceUtc == null || taskZone == null) {
            throw new IllegalArgumentException("UTC reference time and task zone are required");
        }

        // 数据库里的值按 UTC 解释，再切到任务时区，保持实际时刻不变。
        ZonedDateTime referenceInTaskZone = referenceUtc.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(taskZone);

        ZonedDateTime nextInTaskZone = nextAfter(expression, referenceInTaskZone);

        // 算出下一个本地触发点后，转回数据库约定的 UTC 表示。
        return nextInTaskZone.withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }
}