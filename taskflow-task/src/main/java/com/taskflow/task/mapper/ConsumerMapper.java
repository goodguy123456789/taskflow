package com.taskflow.task.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ConsumerMapper {

    int insertProcessedMessage(
            @Param("consumerName") String consumerName,
            @Param("eventId") String eventId
    );

    int insertProjection(
            @Param("taskId") Long taskId,
            @Param("userId") Long userId,
            @Param("eventId") String eventId
    );
}
