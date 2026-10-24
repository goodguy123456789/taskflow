
package com.taskflow.task.entity;

import lombok.Data;

@Data
public class TaskDefinition {

    private Long id;

    private Long ownerUserId;

    private String name;

    private String status;
}
