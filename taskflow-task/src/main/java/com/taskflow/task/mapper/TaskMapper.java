
package com.taskflow.task.mapper;

import com.taskflow.task.entity.TaskDefinition;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TaskMapper {

    @Insert("""
        INSERT INTO task_definition
            (owner_user_id, name, status)
        VALUES
            (#{ownerUserId}, #{name}, #{status})
        """)
    @Options(
        useGeneratedKeys = true,
        keyProperty = "id"
    )
    int insert(TaskDefinition task);

    @Select("""
        SELECT id,
               owner_user_id,
               name,
               status
        FROM task_definition
        WHERE id = #{id}
        """)
    TaskDefinition findById(Long id);
}
