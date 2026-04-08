-- set innodb lock wait timeout
SET SESSION innodb_lock_wait_timeout = 7200;

-- 功能模块排序
update `sys_module` SET `pos` = 9 WHERE `module_key` = 'setting';
update `sys_module` SET `pos` = 6 WHERE `module_key` = 'contract';
update `sys_module` SET `enable` = false WHERE `module_key` = 'business';

-- set innodb lock wait timeout to default
SET SESSION innodb_lock_wait_timeout = DEFAULT;
