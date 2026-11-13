package com.taskflow.user.mapper;

import com.taskflow.user.dto.UserInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {
    UserInfo findActiveById(@Param("id") Long id);
}
