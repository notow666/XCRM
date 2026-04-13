-- 添加客户阶段字段到跟进记录表，记录保存时的客户阶段状态
ALTER TABLE follow_up_record ADD COLUMN stage_status VARCHAR(32) DEFAULT NULL COMMENT '客户阶段状态: NEW/IN_PROGRESS/COMPLETED/FAILED';
ALTER TABLE follow_up_record ADD COLUMN stage VARCHAR(32) DEFAULT NULL COMMENT '客户阶段ID';
