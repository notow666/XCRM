-- set innodb lock wait timeout
SET SESSION innodb_lock_wait_timeout = 7200;

-- Keep report immediately before MMBA audit in sidebar order (pos ascending)
UPDATE sys_module SET pos = 13 WHERE module_key = 'mmbaAudit';

INSERT INTO sys_module (id, organization_id, module_key, enable, pos, create_user, create_time, update_user, update_time)
VALUES (UUID_SHORT(), '100001', 'report', true, 12,
        'admin', UNIX_TIMESTAMP() * 1000, 'admin', UNIX_TIMESTAMP() * 1000);

SET SESSION innodb_lock_wait_timeout = DEFAULT;
