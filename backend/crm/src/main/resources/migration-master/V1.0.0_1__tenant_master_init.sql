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

CREATE TABLE IF NOT EXISTS tenant_db_config (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    db_name VARCHAR(128) NOT NULL,
    jdbc_url VARCHAR(1024) NOT NULL,
    db_username VARCHAR(255) NOT NULL,
    db_password VARCHAR(255) NOT NULL,
    db_driver_class_name VARCHAR(255) NOT NULL DEFAULT 'com.mysql.cj.jdbc.Driver',
    enabled TINYINT NOT NULL DEFAULT 1,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    create_user VARCHAR(64) NOT NULL,
    update_user VARCHAR(64) NOT NULL,
    UNIQUE KEY uk_tenant_db_config_tenant_id (tenant_id)
);

INSERT INTO tenant (id, code, name, status, create_time, update_time, create_user, update_user)
VALUES ('default', 'default', 'Default Tenant', 'ACTIVE', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'system', 'system')
ON DUPLICATE KEY UPDATE update_time = VALUES(update_time);

INSERT INTO tenant_db_config
(id, tenant_id, db_name, jdbc_url, db_username, db_password, db_driver_class_name, enabled, create_time, update_time, create_user, update_user)
VALUES
('default-db', 'default', 'crm_tenant_default',
 'jdbc:mysql://127.0.0.1:3309/crm_tenant_default?autoReconnect=false&useUnicode=true&characterEncoding=UTF-8&characterSetResults=UTF-8&zeroDateTimeBehavior=convertToNull&allowPublicKeyRetrieval=true&useSSL=false&sessionVariables=sql_mode=%27STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION%27',
 'root', 'root', 'com.mysql.cj.jdbc.Driver', 1, UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'system', 'system')
ON DUPLICATE KEY UPDATE update_time = VALUES(update_time);

