-- set innodb lock wait timeout
SET SESSION innodb_lock_wait_timeout = 7200;

-- 客户失败原因配置表
CREATE TABLE IF NOT EXISTS customer_fail_reason_config (
    id VARCHAR(32) NOT NULL COMMENT 'id',
    name VARCHAR(255) NOT NULL COMMENT '原因名称',
    pos INT NOT NULL COMMENT '顺序',
    organization_id VARCHAR(32) NOT NULL COMMENT '组织ID',
    create_time BIGINT NOT NULL COMMENT '创建时间',
    update_time BIGINT NOT NULL COMMENT '更新时间',
    create_user VARCHAR(32) NOT NULL COMMENT '创建人',
    update_user VARCHAR(32) NOT NULL COMMENT '更新人',
    PRIMARY KEY (id),
    INDEX idx_org_id (organization_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户失败原因配置表';

-- 客户跟进方式配置表
CREATE TABLE IF NOT EXISTS customer_follow_way_config (
    id VARCHAR(32) NOT NULL COMMENT 'id',
    name VARCHAR(255) NOT NULL COMMENT '方式名称',
    pos INT NOT NULL COMMENT '顺序',
    organization_id VARCHAR(32) NOT NULL COMMENT '组织ID',
    create_time BIGINT NOT NULL COMMENT '创建时间',
    update_time BIGINT NOT NULL COMMENT '更新时间',
    create_user VARCHAR(32) NOT NULL COMMENT '创建人',
    update_user VARCHAR(32) NOT NULL COMMENT '更新人',
    PRIMARY KEY (id),
    INDEX idx_org_id (organization_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户跟进方式配置表';


-- 初始化跟进方式配置数据
INSERT INTO customer_follow_way_config (id, name, pos, organization_id, create_time, update_time, create_user, update_user)
VALUES
    ('way_phone', '电话', 1, '100001', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'admin'),
    ('way_sms', '短信', 2, '100001', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'admin'),
    ('way_wechat', '微信', 3, '100001', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'admin'),
    ('way_reception', '接待', 4, '100001', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'admin');

SET SESSION innodb_lock_wait_timeout = DEFAULT;
