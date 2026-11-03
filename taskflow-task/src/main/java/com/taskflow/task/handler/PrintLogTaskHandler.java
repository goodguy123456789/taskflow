package com.taskflow.task.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.task.dto.WorkerTaskContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PrintLogTaskHandler implements TaskHandler {

    private final ObjectMapper objectMapper;

    @Override
    public String handlerType() {
        return "PRINT_LOG";
    }

    @Override
    public String execute(WorkerTaskContext context) {
        log.info(
                "执行 PRINT_LOG 任务，instanceId={}，payload={}",
                context.getInstanceId(),
                context.getPayload()
        );

        try {
            return objectMapper.writeValueAsString(
                    Map.of(
                            "instanceId", context.getInstanceId(),
                            "message", "logged"
                    )
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Failed to serialize task result",
                    ex
            );
        }
    }
}