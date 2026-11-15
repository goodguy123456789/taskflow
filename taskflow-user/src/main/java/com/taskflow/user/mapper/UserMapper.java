package com.taskflow.user.mapper;

import com.taskflow.user.dto.UserInfo;

public interface UserMapper {

    UserInfo findActiveById(Long id);

    int insertActiveUser(String username, String passwordHash);

    LoginUser findActiveForLogin(String username);

    record LoginUser(Long id, String passwordHash) {
    }
}
