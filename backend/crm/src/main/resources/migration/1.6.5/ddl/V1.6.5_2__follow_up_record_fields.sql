-- set innodb lock wait timeout
SET SESSION innodb_lock_wait_timeout = 7200;

-- 跟进记录表添加跟进结果和失败原因字段
ALTER TABLE follow_up_record ADD COLUMN follow_result VARCHAR(32) DEFAULT NULL COMMENT '跟进结果: IN_PROGRESS-跟进中, COMPLETED-跟进完成, FAILED-无效' AFTER contact_id;
ALTER TABLE follow_up_record ADD COLUMN fail_reason VARCHAR(255) DEFAULT NULL COMMENT '失败原因' AFTER follow_result;

SET SESSION innodb_lock_wait_timeout = DEFAULT;
