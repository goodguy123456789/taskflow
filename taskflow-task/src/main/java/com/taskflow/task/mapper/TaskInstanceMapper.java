package com.taskflow.task.mapper;

import com.taskflow.task.entity.TaskInstance;
import com.taskflow.task.dto.WorkerTaskContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskInstanceMapper {
    int renewLease(
        @Param("instanceId") Long instanceId,
        @Param("workerId") String workerId,
        @Param("leaseVersion") Long leaseVersion,
        @Param("leaseSeconds") int leaseSeconds
    );
    int markFailed(@Param("instanceId") Long instanceId,
                   @Param("workerId") String workerId,
                   @Param("leaseVersion") Long leaseVersion);
    int markSuccess(
        @Param("instanceId") Long instanceId,
        @Param("workerId") String workerId,
        @Param("leaseVersion") Long leaseVersion
    );

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
