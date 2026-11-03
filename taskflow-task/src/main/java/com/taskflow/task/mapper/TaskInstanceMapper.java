package com.taskflow.task.mapper;

import com.taskflow.task.entity.TaskInstance;
import com.taskflow.task.dto.WorkerTaskContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskInstanceMapper {

    int insert(TaskInstance instance);

    TaskInstance findById(@Param("id") Long id);

     int claimQueued(
        @Param("instanceId") Long instanceId,
        @Param("workerId") String workerId,
        @Param("leaseSeconds") int leaseSeconds
    );

    WorkerTaskContext findWorkerContext(
        @Param("instanceId") Long instanceId
   );
}
