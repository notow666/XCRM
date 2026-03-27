CREATE TABLE IF NOT EXISTS platform_user (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    create_user VARCHAR(64) NOT NULL,
    update_user VARCHAR(64) NOT NULL
);

CREATE TABLE IF NOT EXISTS platform_login_log (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    result VARCHAR(32) NOT NULL,
    detail VARCHAR(512) NULL,
    create_time BIGINT NOT NULL
);

INSERT INTO platform_user (id, username, password_hash, status, create_time, update_time, create_user, update_user)
VALUES ('platform-admin', 'xlj-admin', MD5('xlj123'), 'ACTIVE', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'system', 'system')
ON DUPLICATE KEY UPDATE update_time = VALUES(update_time);
