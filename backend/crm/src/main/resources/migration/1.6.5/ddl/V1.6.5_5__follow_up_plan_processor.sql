-- 添加跟进计划预计处理人员字段
ALTER TABLE follow_up_plan ADD COLUMN processor VARCHAR(32) DEFAULT NULL COMMENT '预计处理人员';
