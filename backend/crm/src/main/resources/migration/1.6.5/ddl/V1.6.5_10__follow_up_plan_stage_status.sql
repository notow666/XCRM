-- 添加阶段状态字段到跟进计划表
ALTER TABLE follow_up_plan ADD COLUMN next_stage_status VARCHAR(32) DEFAULT NULL COMMENT '阶段状态: NEW/IN_PROGRESS/COMPLETED/FAILED';