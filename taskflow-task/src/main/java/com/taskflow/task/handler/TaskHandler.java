package com.taskflow.task.handler;

import com.taskflow.task.dto.WorkerTaskContext;

public interface TaskHandler {

    /**
     * 这个 Handler 能处理的任务类型。
     */
    String handlerType();

    /**
     * 执行任务，并返回合法的 JSON 结果。
     */
    String execute(WorkerTaskContext context);
}