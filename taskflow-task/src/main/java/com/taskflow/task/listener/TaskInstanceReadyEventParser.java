package com.taskflow.task.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.task.dto.TaskInstanceReadyEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TaskInstanceReadyEventParser {

    private static final String READY_EVENT_TYPE =
            "TASK_INSTANCE_READY";

    private final ObjectMapper objectMapper;

    public TaskInstanceReadyEvent parse(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException(
                    "Task instance ready message is empty"
            );
        }

        final TaskInstanceReadyEvent event;

        try {
            event = objectMapper.readValue(
                    json,
                    TaskInstanceReadyEvent.class
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException(
                    "Invalid task instance ready message",
                    ex
            );
        }

        if (event == null) {
            throw new IllegalArgumentException(
                    "Task instance ready message is null"
            );
        }

        if (event.eventId() == null || event.eventId().isBlank()) {
            throw new IllegalArgumentException(
                    "eventId is required"
            );
        }

        if (!READY_EVENT_TYPE.equals(event.eventType())) {
            throw new IllegalArgumentException(
                    "Unexpected eventType: " + event.eventType()
            );
        }

        if (event.instanceId() == null || event.instanceId() <= 0) {
            throw new IllegalArgumentException(
                    "instanceId must be greater than zero"
            );
        }

        return event;
    }
}