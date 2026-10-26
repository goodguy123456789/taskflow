package com.taskflow.task.mapper;

import com.taskflow.task.entity.OutboxEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OutboxMapper {

    int insert(OutboxEvent event);
}
