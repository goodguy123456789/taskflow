package com.taskflow.task.handler;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TaskHandlerRegistry {

    private final Map<String, TaskHandler> handlersByType;

    public TaskHandlerRegistry(List<TaskHandler> handlers) {
        Map<String, TaskHandler> registeredHandlers = new HashMap<>();

        for (TaskHandler handler : handlers) {
            String type = handler.handlerType();

            if (type == null || type.isBlank()) {
                throw new IllegalStateException(
                        "Task handler type must not be empty"
                );
            }

            TaskHandler existing =
                    registeredHandlers.putIfAbsent(type, handler);

            if (existing != null) {
                throw new IllegalStateException(
                        "Duplicate task handler type: " + type
                );
            }
        }

        this.handlersByType = Map.copyOf(registeredHandlers);
    }

    public TaskHandler getHandler(String handlerType) {
        TaskHandler handler = handlersByType.get(handlerType);

        if (handler == null) {
            throw new IllegalArgumentException(
                    "Unknown task handler type: " + handlerType
            );
        }

        return handler;
    }
}