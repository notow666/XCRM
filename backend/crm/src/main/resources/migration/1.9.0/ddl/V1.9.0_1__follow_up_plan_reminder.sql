ALTER TABLE follow_up_plan
    ADD COLUMN remind_time BIGINT DEFAULT NULL COMMENT '提醒时间',
    ADD COLUMN remind_status VARCHAR(32) DEFAULT NULL COMMENT '提醒状态:PENDING/SENT/CANCELLED',
    ADD COLUMN reminded_time BIGINT DEFAULT NULL COMMENT '实际提醒时间';

CREATE INDEX idx_follow_up_plan_remind ON follow_up_plan (organization_id, remind_status, remind_time);
