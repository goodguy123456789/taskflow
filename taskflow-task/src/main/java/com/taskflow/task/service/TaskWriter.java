
package com.taskflow.task.service;

import com.taskflow.task.entity.TaskDefinition;
import com.taskflow.task.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskWriter {

    private final TaskMapper taskMapper;

    @Transactional
    public Long insertTask(TaskDefinition task) {

        int rows = taskMapper.insert(task);

        if (rows != 1 || task.getId() == null) {
            throw new IllegalStateException(
                    "Failed to insert task definition");
        }

        return task.getId();
    }
}
