
package com.taskflow.task.mapper;

import com.taskflow.task.entity.OutboxEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OutboxMapper {

    int insert(OutboxEvent event);

    List<OutboxEvent> selectClaimable(
            @Param("limit") int limit
    );

    int claim(
            @Param("id") Long id,
            @Param("token") String token,
            @Param("leaseSeconds") int leaseSeconds
    );

    int markPublished(
            @Param("id") Long id,
            @Param("token") String token
    );

    int scheduleRetry(
            @Param("id") Long id,
            @Param("token") String token,
            @Param("delaySeconds") long delaySeconds
    );
}
