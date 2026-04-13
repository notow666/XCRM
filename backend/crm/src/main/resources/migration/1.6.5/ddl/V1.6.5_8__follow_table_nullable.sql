-- set innodb lock wait timeout
SET SESSION innodb_lock_wait_timeout = 7200;

-- 跟进记录表：跟进类型字段改为允许NULL（前端不再使用该字段）
ALTER TABLE follow_up_record MODIFY COLUMN type VARCHAR(32) DEFAULT NULL COMMENT '类型';

-- 跟进计划表：类型字段改为允许NULL（前端不再使用该字段）
ALTER TABLE follow_up_plan MODIFY COLUMN type VARCHAR(32) DEFAULT NULL COMMENT '类型';

SET SESSION innodb_lock_wait_timeout = DEFAULT;
