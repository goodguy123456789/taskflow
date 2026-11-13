package com.taskflow.task.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskExecutionMapper {
       // 记录一次可重试的失败，同时保存本次执行的重试到期时间。
    int markFailedForRetry(
            @Param("instanceId") Long instanceId,
            @Param("attemptNo") int attemptNo,
            @Param("workerId") String workerId,
            @Param("leaseVersion") Long leaseVersion,
            @Param("errorMessage") String errorMessage,
            @Param("retryDelaySeconds") long retryDelaySeconds
    );
    int markFailed(@Param("instanceId") Long instanceId,
                   @Param("attemptNo") int attemptNo,
                   @Param("workerId") String workerId,
                   @Param("leaseVersion") Long leaseVersion,
                   @Param("errorMessage") String errorMessage);
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
