-- set innodb lock wait timeout
SET SESSION innodb_lock_wait_timeout = 7200;

ALTER TABLE sys_operation_log
    ADD COLUMN trace_id VARCHAR(64) NULL COMMENT 'traceId' AFTER organization_id;

CREATE INDEX idx_trace_id ON sys_operation_log (trace_id ASC);

-- set innodb lock wait timeout to default
SET SESSION innodb_lock_wait_timeout = DEFAULT;
