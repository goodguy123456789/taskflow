
package com.taskflow.task.mapper;

import com.taskflow.task.entity.TaskDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskMapper {

    int insert(TaskDefinition task);

    TaskDefinition findById(@Param("id") Long id);
}
