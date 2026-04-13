-- 添加跟进计划显示字段
ALTER TABLE follow_up_plan ADD COLUMN customer_name VARCHAR(100) DEFAULT NULL COMMENT '客户名称';
ALTER TABLE follow_up_plan ADD COLUMN next_stage_name VARCHAR(100) DEFAULT NULL COMMENT '节点名称';
ALTER TABLE follow_up_plan ADD COLUMN owner_name VARCHAR(100) DEFAULT NULL COMMENT '负责人名称';
