
package com.taskflow.task.mapper;

import com.taskflow.task.entity.TaskDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TaskMapper {
    List<TaskDefinition> selectDueOnceForUpdate(
        @Param("limit") int limit
    );

    int markOnceScheduled(
        @Param("id") Long id,
        @Param("scheduledAt") LocalDateTime scheduledAt
    );
    int insert(TaskDefinition task);

    TaskDefinition findById(@Param("id") Long id);

    TaskDefinition findByUserAndIdempotencyKey(
            @Param("userId") Long userId,
            @Param("key") String key
    );
}
