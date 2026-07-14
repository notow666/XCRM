SET SESSION innodb_lock_wait_timeout = 7200;

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT UUID_SHORT(), 'org_admin', 'SYS_ORGANIZATION_USER:CLEAN_CALL_LOG'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission
    WHERE role_id = 'org_admin' AND permission_id = 'SYS_ORGANIZATION_USER:CLEAN_CALL_LOG'
);

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT UUID_SHORT(), 'org_admin', 'CALL_LOG_CLEAN_CONFIG:UPDATE'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission
    WHERE role_id = 'org_admin' AND permission_id = 'CALL_LOG_CLEAN_CONFIG:UPDATE'
);

SET SESSION innodb_lock_wait_timeout = DEFAULT;
