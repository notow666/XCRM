CREATE TABLE IF NOT EXISTS platform_audit_log (
    id VARCHAR(64) PRIMARY KEY,
    operator_id VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NULL,
    result VARCHAR(32) NOT NULL,
    detail VARCHAR(2048) NULL,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    create_time BIGINT NOT NULL
);

CREATE INDEX idx_platform_audit_log_tenant_time ON platform_audit_log (tenant_id, create_time);
CREATE INDEX idx_platform_audit_log_operator_time ON platform_audit_log (operator_id, create_time);

CREATE TABLE IF NOT EXISTS tenant_ops_task (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    task_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    detail VARCHAR(2048) NULL,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    operator_id VARCHAR(64) NOT NULL
);

CREATE INDEX idx_tenant_ops_task_tenant_time ON tenant_ops_task (tenant_id, create_time);
