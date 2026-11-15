CREATE DATABASE IF NOT EXISTS taskflow_user CHARACTER SET utf8mb4;

-- MySQL 镜像先创建 taskflow 实验账号，User 服务需要查询和注册权限。
GRANT SELECT, INSERT ON taskflow_user.* TO 'taskflow'@'%';

CREATE TABLE taskflow_user.sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
);
