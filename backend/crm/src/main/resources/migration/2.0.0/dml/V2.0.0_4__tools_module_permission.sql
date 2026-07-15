-- set innodb lock wait timeout
SET SESSION innodb_lock_wait_timeout = 7200;

INSERT INTO sys_module (id, organization_id, module_key, enable, pos, create_user, create_time, update_user, update_time)
SELECT UUID_SHORT(), '100001', 'tools', true, 14, 'admin', UNIX_TIMESTAMP() * 1000, 'admin', UNIX_TIMESTAMP() * 1000
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_module WHERE organization_id = '100001' AND module_key = 'tools'
);

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT UUID_SHORT(), 'org_admin', 'NUMBER_CUBE:READ'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission WHERE role_id = 'org_admin' AND permission_id = 'NUMBER_CUBE:READ'
);

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT UUID_SHORT(), 'org_admin', 'NUMBER_CUBE:ADD'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission WHERE role_id = 'org_admin' AND permission_id = 'NUMBER_CUBE:ADD'
);

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT UUID_SHORT(), 'org_admin', 'NUMBER_CUBE:DOWNLOAD'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission WHERE role_id = 'org_admin' AND permission_id = 'NUMBER_CUBE:DOWNLOAD'
);

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT UUID_SHORT(), 'org_admin', 'NUMBER_CUBE:DELETE'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission WHERE role_id = 'org_admin' AND permission_id = 'NUMBER_CUBE:DELETE'
);

SET SESSION innodb_lock_wait_timeout = DEFAULT;
