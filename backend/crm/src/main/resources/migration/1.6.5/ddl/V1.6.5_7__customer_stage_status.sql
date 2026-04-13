-- set innodb lock wait timeout
SET SESSION innodb_lock_wait_timeout = 7200;

-- 客户表添加阶段状态和失败原因字段
ALTER TABLE customer ADD COLUMN stage_status VARCHAR(20) DEFAULT NULL COMMENT '阶段状态: NEW-待跟进, IN_PROGRESS-跟进中, COMPLETED-已跟进, FAILED-无效' AFTER stage;
ALTER TABLE customer ADD COLUMN fail_reason VARCHAR(255) DEFAULT NULL COMMENT '失败原因' AFTER stage_status;

-- 客户阶段配置表添加是否固定字段
ALTER TABLE customer_stage_config ADD COLUMN is_fixed BIT(1) NOT NULL DEFAULT 0 COMMENT '是否固定节点: 1-固定不可删除, 0-可自定义' AFTER organization_id;

-- 删除旧的阶段配置数据（用新的替代）
DELETE FROM customer_stage_config WHERE organization_id = '100001';

-- 重新初始化客户阶段配置数据（organization_id = 100001）
-- 固定节点：跟进（pos=1）、回款（pos=4）、无效客户（pos=5）
-- 自定义节点：上门（pos=2）、签约（pos=3）
INSERT INTO customer_stage_config (id, name, type, rate, afoot_roll_back, end_roll_back, pos, organization_id, create_time, update_time, create_user, update_user, is_fixed)
VALUES
    ('stage_follow', '跟进', 'AFOOT', '10', b'0', b'0', 1, '100001', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'admin', b'1'),
    ('stage_visit', '上门', 'AFOOT', '30', b'0', b'0', 2, '100001', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'admin', b'0'),
    ('stage_sign', '签约', 'AFOOT', '60', b'0', b'0', 3, '100001', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'admin', b'0'),
    ('stage_payment', '回款', 'END', '100', b'0', b'1', 4, '100001', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'admin', b'1'),
    ('stage_fail', '无效客户', 'END', '0', b'0', b'0', 5, '100001', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'admin', b'1');

SET SESSION innodb_lock_wait_timeout = DEFAULT;
