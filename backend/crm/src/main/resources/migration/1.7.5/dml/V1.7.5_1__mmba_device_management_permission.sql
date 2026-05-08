-- MMBA 设备管理：仅组织管理员拥有菜单与操作权限
SET SESSION innodb_lock_wait_timeout = 7200;

INSERT INTO sys_role_permission (id, role_id, permission_id)
VALUES (UUID_SHORT(), 'org_admin', 'MMBA_DEVICE:READ');
INSERT INTO sys_role_permission (id, role_id, permission_id)
VALUES (UUID_SHORT(), 'org_admin', 'MMBA_DEVICE:ADD');
INSERT INTO sys_role_permission (id, role_id, permission_id)
VALUES (UUID_SHORT(), 'org_admin', 'MMBA_DEVICE:UPDATE');
INSERT INTO sys_role_permission (id, role_id, permission_id)
VALUES (UUID_SHORT(), 'org_admin', 'MMBA_DEVICE:IMPORT');

SET SESSION innodb_lock_wait_timeout = DEFAULT;
