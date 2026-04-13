-- 禁用顶部导航栏"记录/计划"入口，功能移至客户详情页的跟进记录/跟进计划Tab
SET SESSION innodb_lock_wait_timeout = 7200;

UPDATE sys_navigation SET enable = false WHERE navigation_key = 'event';

SET SESSION innodb_lock_wait_timeout = DEFAULT;