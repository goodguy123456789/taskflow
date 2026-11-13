-- TaskFlow development schema only
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS=0;
CREATE TABLE `processed_message` (
  `consumer_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '消费逻辑名称，创建事件消费者使用 task-created-projection',
  `event_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '收到的事件 ID，与 consumer_name 组成去重主键',
  `processed_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '去重登记写入时间，和投影写入一起提交或回滚',
  PRIMARY KEY (`consumer_name`,`event_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='消息消费去重表，按消费者名称和事件 ID 登记';

CREATE TABLE `task_created_projection` (
  `task_id` bigint NOT NULL COMMENT '消息中的任务定义 ID，对应 task_definition.id，也是本表主键',
  `user_id` bigint NOT NULL COMMENT '创建消息中携带的任务所属用户 ID',
  `event_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '生成这行投影的 TASK_DEFINITION_CREATED 事件 ID',
  PRIMARY KEY (`task_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='任务创建事件投影表，保存创建消息消费后提取的数据';

CREATE TABLE `task_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务定义主键，实例用 definition_id 关联',
  `user_id` bigint NOT NULL COMMENT '任务定义所属用户的 ID',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户填写的任务名称',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '用户填写的任务说明，可为空',
  `handler_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '执行器类型，Worker 据此选择任务处理逻辑',
  `trigger_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '触发方式，ONCE 表示一次性，CRON 表示周期性',
  `cron_expression` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '周期任务的 Cron 表达式，一次性任务可为空',
  `timezone` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'UTC' COMMENT '解释 Cron 表达式时使用的时区，例如 Asia/Shanghai',
  `next_trigger_at` datetime(3) DEFAULT NULL COMMENT '下一次计划触发时间，按 UTC 保存；为空时调度器跳过',
  `payload` json DEFAULT NULL COMMENT '任务执行参数，生成实例时复制到实例中',
  `max_retries` int NOT NULL DEFAULT '3' COMMENT '首次执行失败后允许的最大重试次数',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'ENABLED' COMMENT '定义状态，ENABLED 参与调度，DISABLED 停止调度',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '任务定义记录的创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '任务定义记录最近一次更新时间',
  `idempotency_key` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '客户端创建请求的幂等键，与 user_id 组合去重',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_idempotency` (`user_id`,`idempotency_key`) USING BTREE,
  KEY `idx_user_created` (`user_id`,`created_at`) USING BTREE,
  KEY `idx_definition_schedule` (`status`,`next_trigger_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='任务定义表，保存任务配置和调度规则';

CREATE TABLE `task_execution` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '执行尝试记录主键',
  `instance_id` bigint NOT NULL COMMENT '本次尝试所属的任务实例 ID',
  `attempt_no` int NOT NULL COMMENT '实例内的尝试序号，从 1 开始；与 instance_id 组合唯一',
  `worker_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '执行本次尝试的 Worker 标识',
  `lease_version` bigint NOT NULL COMMENT '本次尝试领取实例时取得的租约版本，用于校验结果写回',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '本次尝试的状态，RUNNING 执行中、SUCCESS 成功、FAILED 失败',
  `start_time` datetime(3) DEFAULT NULL COMMENT '本次尝试开始时间，按 UTC 保存',
  `end_time` datetime(3) DEFAULT NULL COMMENT '本次尝试结束时间，按 UTC 保存',
  `error_message` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '本次尝试失败时记录的错误信息',
  `result_summary` json DEFAULT NULL COMMENT '本次尝试成功时写入的 JSON 结果摘要',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '执行尝试记录的创建时间',
  `retry_due_at` datetime(3) DEFAULT NULL COMMENT '本次失败允许重试时，按退避和抖动算出的最早重试时间，UTC',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_instance_attempt` (`instance_id`,`attempt_no`) USING BTREE,
  KEY `idx_execution_status_time` (`status`,`start_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='任务执行尝试记录表，同一实例重试时会新增一条尝试记录';

CREATE TABLE `task_instance` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务实例主键，Ready 消息通过 instanceId 指向这一行',
  `definition_id` bigint NOT NULL COMMENT '生成该实例的任务定义 ID，与 scheduled_at 组合防止重复生成',
  `user_id` bigint NOT NULL COMMENT '生成实例时从任务定义复制的所属用户 ID',
  `scheduled_at` datetime(3) NOT NULL COMMENT '本次运行的计划触发时间，按 UTC 保存',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING' COMMENT '实例状态：PENDING 待领取、QUEUED 已排队、RUNNING 执行中、RETRY_WAIT 等待重试、SUCCESS 成功、FAILED 最终失败',
  `handler_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '生成实例时复制的执行器类型',
  `payload` json DEFAULT NULL COMMENT '生成实例时复制的执行参数，Worker 从这里读取本次运行的输入',
  `max_retries` int NOT NULL DEFAULT '3' COMMENT '生成实例时复制的最大重试次数，不含首次执行',
  `attempt_count` int NOT NULL DEFAULT '0' COMMENT '已领取的执行尝试次数，Worker 每次成功领取时加一',
  `lease_owner` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '当前持有执行租约的 Worker 标识',
  `lease_until` datetime(3) DEFAULT NULL COMMENT '执行租约到期时间，按 UTC 保存；到期后恢复逻辑可接管',
  `lease_version` bigint NOT NULL DEFAULT '0' COMMENT '执行租约版本，每次领取时递增，用来阻止旧 Worker 写回结果',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '调度器创建这条实例记录的时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '实例记录最近一次更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_definition_scheduled` (`definition_id`,`scheduled_at`) USING BTREE,
  KEY `idx_instance_status` (`status`,`created_at`) USING BTREE,
  KEY `idx_instance_lease` (`status`,`lease_until`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='任务实例表，每行代表任务定义的一次计划运行';

CREATE TABLE `task_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Outbox 数据库记录主键，Relay 按此字段排序领取',
  `event_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '消息事件的全局唯一 ID，重发同一事件时保持不变',
  `aggregate_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '事件关联的业务对象类型，例如 TASK_DEFINITION 或 TASK_INSTANCE',
  `aggregate_id` bigint NOT NULL COMMENT '事件关联的业务对象 ID，具体指向由 aggregate_type 决定',
  `event_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '事件类型，Relay 据此选择 RabbitMQ 路由键',
  `payload` json NOT NULL COMMENT '发布给 RabbitMQ 的 JSON 消息正文',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'NEW' COMMENT '发布状态，NEW 待发布、PUBLISHING 已领取发布中、PUBLISHED 已确认发布',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT 'Relay 发布失败后重新安排投递的次数',
  `next_retry_at` datetime(3) DEFAULT NULL COMMENT 'Relay 发布失败后最早可再次投递的时间',
  `published_at` datetime(3) DEFAULT NULL COMMENT '收到 Broker 发布确认且消息成功路由后记录的时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '业务代码将事件写入 Outbox 的时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Outbox 记录最近一次更新时间',
  `claim_token` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Relay 本次领取的令牌，更新发布结果时用它校验领取权',
  `locked_until` datetime(3) DEFAULT NULL COMMENT 'Relay 发布租约到期时间，超时未完成的事件可以重新领取',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_event_id` (`event_id`) USING BTREE,
  KEY `idx_outbox_pending` (`status`,`next_retry_at`,`id`) USING BTREE,
  KEY `idx_outbox_lease` (`status`,`locked_until`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='可靠消息 Outbox 事件表，保存待发布事件及发布状态';

SET FOREIGN_KEY_CHECKS=1;
