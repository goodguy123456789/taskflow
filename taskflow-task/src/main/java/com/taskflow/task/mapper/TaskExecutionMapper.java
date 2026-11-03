package com.taskflow.task.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskExecutionMapper {
    int markSuccess(
        @Param("instanceId") Long instanceId,
        @Param("attemptNo") int attemptNo,
        @Param("workerId") String workerId,
        @Param("leaseVersion") Long leaseVersion,
        @Param("resultJson") String resultJson
    );

    int insertRunningAttempt(
            @Param("instanceId") Long instanceId,
            @Param("attemptNo") int attemptNo,
            @Param("workerId") String workerId,
            @Param("leaseVersion") Long leaseVersion);
}
