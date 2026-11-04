package com.taskflow.task.mapper;

import com.taskflow.task.entity.TaskInstance;
import com.taskflow.task.dto.WorkerTaskContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TaskInstanceMapper {
    List<TaskInstance> selectExpiredForUpdate(@Param("limit") int limit);

    int markRetryWait(@Param("instanceId") Long instanceId,
                      @Param("workerId") String workerId,
                      @Param("leaseVersion") Long leaseVersion);
    List<TaskInstance> selectDueRetriesForUpdate(@Param("limit") int limit,
                                                 @Param("delaySeconds") int delaySeconds);
    int queueRetry(@Param("id") Long id, @Param("attemptCount") int attemptCount);
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
