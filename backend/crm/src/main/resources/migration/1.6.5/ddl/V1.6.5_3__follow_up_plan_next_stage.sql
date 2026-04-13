-- set innodb lock wait timeout
SET SESSION innodb_lock_wait_timeout = 7200;

-- 跟进计划表添加下一阶段字段
ALTER TABLE follow_up_plan ADD COLUMN next_stage VARCHAR(32) DEFAULT NULL COMMENT '下一阶段ID' AFTER converted;

SET SESSION innodb_lock_wait_timeout = DEFAULT;
