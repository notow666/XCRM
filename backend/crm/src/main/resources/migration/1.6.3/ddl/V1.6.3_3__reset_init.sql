-- set innodb lock wait timeout
SET SESSION innodb_lock_wait_timeout = 7200;

-- 顶部导航栏
update sys_navigation set enable = false where navigation_key = 'agent';
update sys_navigation set enable = false where navigation_key = 'about';
update sys_navigation set enable = false where navigation_key = 'language';
update sys_navigation set enable = false where navigation_key = 'help';

-- 功能模块
update sys_module set enable = false where module_key = 'dashboard';
update sys_module set enable = false where module_key = 'agent';
update sys_module set enable = false where module_key = 'tender';
update sys_module set enable = false where module_key = 'order';
update sys_module set enable = false where module_key = 'product';

-- set innodb lock wait timeout to default
SET SESSION innodb_lock_wait_timeout = DEFAULT;
