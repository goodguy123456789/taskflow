// package com.taskflow.task.scheduler;

// import com.taskflow.task.service.SchedulerDispatchService;

// import lombok.RequiredArgsConstructor;
// import lombok.extern.slf4j.Slf4j;

// import org.springframework.scheduling.annotation.Scheduled;
// import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
// import org.springframework.stereotype.Component;

// @Slf4j
// @Component
// @RequiredArgsConstructor
// // 总开关和内置到期扫描开关都允许时，才启用这个 Job。
// @ConditionalOnProperty(
//         prefix = "taskflow.scheduler",
//         name = {"enabled", "due-scan-enabled"},
//         havingValue = "true",
//         matchIfMissing = true
// )
// public class TaskSchedulerJob {

//     private final SchedulerDispatchService dispatchService;

//     @Scheduled(
//         fixedDelayString =
//                 "${taskflow.scheduler.poll-delay-ms:5000}",
//         initialDelay = 10000
//     )
//     public void scanDueTasks() {

//         try {
//             int count =
//                     dispatchService.scheduleDueBatch();

//             if (count > 0) {
//                 log.info(
//                         "Scheduler created {} task instances",
//                         count
//                 );
//             }

//         } catch (Exception ex) {
//             log.error("Scheduler batch failed", ex);
//         }
//     }
// }
