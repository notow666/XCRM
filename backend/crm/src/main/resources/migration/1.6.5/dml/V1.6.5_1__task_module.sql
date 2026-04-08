-- set innodb lock wait timeout
SET SESSION innodb_lock_wait_timeout = 7200;

update `sys_module` SET `pos` = 5 WHERE `module_key` = 'business';
update `sys_module` SET `pos` = 6 WHERE `module_key` = 'product';
update `sys_module` SET `pos` = 7 WHERE `module_key` = 'contract';
update `sys_module` SET `pos` = 8 WHERE `module_key` = 'dashboard';
update `sys_module` SET `pos` = 9 WHERE `module_key` = 'agent';
update `sys_module` SET `pos` = 10 WHERE `module_key` = 'tender';
update `sys_module` SET `pos` = 11 WHERE `module_key` = 'order';
update `sys_module` SET `pos` = 99 WHERE `module_key` = 'setting';

INSERT INTO sys_module (id, organization_id, module_key, enable, pos, create_user, create_time, update_user, update_time)
VALUES (UUID_SHORT(), '100001', 'task', true, 4,
        'admin', UNIX_TIMESTAMP() * 1000, 'admin', UNIX_TIMESTAMP() * 1000);

INSERT INTO sys_role_permission (id, role_id, permission_id)
VALUES (UUID_SHORT(), 'org_admin', 'TASK:READ');
INSERT INTO sys_role_permission (id, role_id, permission_id)
VALUES (UUID_SHORT(), 'org_admin', 'TASK:COMPLETE');

INSERT INTO sys_role_permission (id, role_id, permission_id)
VALUES (UUID_SHORT(), 'sales_manager', 'TASK:READ');
INSERT INTO sys_role_permission (id, role_id, permission_id)
VALUES (UUID_SHORT(), 'sales_manager', 'TASK:COMPLETE');

INSERT INTO sys_role_permission (id, role_id, permission_id)
VALUES (UUID_SHORT(), 'sales_staff', 'TASK:READ');
INSERT INTO sys_role_permission (id, role_id, permission_id)
VALUES (UUID_SHORT(), 'sales_staff', 'TASK:COMPLETE');

SET SESSION innodb_lock_wait_timeout = DEFAULT;
