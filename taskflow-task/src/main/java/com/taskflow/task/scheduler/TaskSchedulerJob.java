package com.taskflow.task.scheduler;

import com.taskflow.task.service.SchedulerDispatchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskSchedulerJob {

    private final SchedulerDispatchService dispatchService;

    @Scheduled(
        fixedDelayString =
                "${taskflow.scheduler.poll-delay-ms:5000}",
        initialDelay = 10000
    )
    public void scanDueTasks() {

        try {
            int count =
                    dispatchService.scheduleDueOnceBatch();

            if (count > 0) {
                log.info(
                        "Scheduler created {} task instances",
                        count
                );
            }

        } catch (Exception ex) {
            log.error("Scheduler batch failed", ex);
        }
    }
}
