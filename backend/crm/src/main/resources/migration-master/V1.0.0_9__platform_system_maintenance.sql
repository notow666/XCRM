CREATE TABLE IF NOT EXISTS platform_system_announcement (
    id VARCHAR(64) PRIMARY KEY,
    subject VARCHAR(512) NOT NULL,
    content TEXT NOT NULL,
    operator_id VARCHAR(64) NOT NULL,
    create_time BIGINT NOT NULL
);

CREATE INDEX idx_platform_system_announcement_time ON platform_system_announcement (create_time DESC);
