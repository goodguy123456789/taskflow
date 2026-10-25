package com.taskflow.task.entity;

import lombok.Data;

@Data
public class TaskDefinition {

    private Long id;

    private Long userId;

    private String name;

    private String handlerType;

    private String triggerType;

    private String status;
}