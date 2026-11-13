
package com.taskflow.task.mapper;

import com.taskflow.task.entity.TaskDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TaskMapper {
    LocalDateTime selectUtcNow();

    List<TaskDefinition> selectDueForUpdate(
        @Param("limit") int limit
    );

    int advanceSchedule(
        @Param("id") Long id,
        @Param("triggerType") String triggerType,
        @Param("scheduledAt") LocalDateTime scheduledAt,
        @Param("nextTriggerAt") LocalDateTime nextTriggerAt
    );
    int insert(TaskDefinition task);

    LocalDateTime findDispatchedOnceScheduledAt(@Param("definitionId") Long definitionId);

    TaskDefinition findById(@Param("id") Long id);

    TaskDefinition findByUserAndIdempotencyKey(
            @Param("userId") Long userId,
            @Param("key") String key
    );
}
