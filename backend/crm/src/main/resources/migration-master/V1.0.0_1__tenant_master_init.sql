CREATE TABLE IF NOT EXISTS tenant (
    id VARCHAR(64) PRIMARY KEY,
    code VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    create_user VARCHAR(64) NOT NULL,
    update_user VARCHAR(64) NOT NULL
);

INSERT INTO tenant (id, code, name, status, create_time, update_time, create_user, update_user)
VALUES ('default', 'default', 'Default Tenant', 'ACTIVE', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'system', 'system')
ON DUPLICATE KEY UPDATE update_time = VALUES(update_time);

