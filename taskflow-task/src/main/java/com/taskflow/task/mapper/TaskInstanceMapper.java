package com.taskflow.task.mapper;

import com.taskflow.task.entity.TaskInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskInstanceMapper {

    int insert(TaskInstance instance);

    TaskInstance findById(@Param("id") Long id);
}
