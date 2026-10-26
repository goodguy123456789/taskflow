CREATE TABLE task_definition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    handler_type VARCHAR(64) NOT NULL,
    trigger_type VARCHAR(20) NOT NULL,
    cron_expression VARCHAR(128),
    timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    next_trigger_at TIMESTAMP(3),
    payload JSON,
    max_retries INT NOT NULL DEFAULT 3,
    idempotency_key VARCHAR(36),
    CONSTRAINT uk_user_idempotency UNIQUE (user_id, idempotency_key),
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);
CREATE TABLE task_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL UNIQUE,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP(3),
    published_at TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);
