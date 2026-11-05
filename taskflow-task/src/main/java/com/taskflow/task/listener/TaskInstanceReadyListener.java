package com.taskflow.task.listener;

import com.taskflow.task.config.RabbitConfig;
import com.taskflow.task.dto.TaskInstanceReadyEvent;
import com.taskflow.task.entity.TaskInstance;
import com.taskflow.task.mapper.TaskInstanceMapper;
import com.taskflow.task.service.TaskWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class TaskInstanceReadyListener {

    private final TaskInstanceReadyEventParser parser;
    private final TaskWorker taskWorker;
    private final TaskInstanceMapper instanceMapper;

    /**
     * 当前服务进程中的 Worker 标识。
     *
     * 每次创建 Listener 对象时生成一个新值，
     * 用于记录实例租约的持有者。
     */
    private final String workerId =
            "worker-" + UUID.randomUUID();

    public TaskInstanceReadyListener(
            TaskInstanceReadyEventParser parser,
            TaskWorker taskWorker,
            TaskInstanceMapper instanceMapper
    ) {
        this.parser = parser;
        this.taskWorker = taskWorker;
        this.instanceMapper = instanceMapper;
    }

    @RabbitListener(
            queues = RabbitConfig.INSTANCE_READY_QUEUE,
            autoStartup = "${taskflow.worker.listener-enabled:false}"
    )
    public void handle(String json) {
        TaskInstanceReadyEvent event = parser.parse(json);

        TaskWorker.ExecutionOutcome outcome =
                taskWorker.execute(
                        event.instanceId(),
                        workerId
                );

        if (outcome == TaskWorker.ExecutionOutcome.NOT_CLAIMED) {
            TaskInstance instance = instanceMapper.findById(event.instanceId());
            if (instance == null) {
                throw new IllegalStateException("Task instance not found: " + event.instanceId());
            }
            if ("SUCCESS".equals(instance.getStatus()) || "FAILED".equals(instance.getStatus())) {
                log.info("Terminal task instance ignored: instanceId={}, status={}",
                        event.instanceId(), instance.getStatus());
                return;
            }
            throw new IllegalStateException("Task instance was not claimed: "
                    + event.instanceId() + ", status=" + instance.getStatus());
        }

        log.info(
                "Task instance handled: instanceId={}, "
                        + "eventId={}, outcome={}",
                event.instanceId(),
                event.eventId(),
                outcome
        );
    }
}