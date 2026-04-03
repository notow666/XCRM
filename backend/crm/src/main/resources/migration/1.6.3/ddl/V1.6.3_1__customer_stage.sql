-- set innodb lock wait timeout
SET SESSION innodb_lock_wait_timeout = 7200;

-- 客户阶段配置表
CREATE TABLE IF NOT EXISTS customer_stage_config
(
    `id`                VARCHAR(32)  NOT NULL COMMENT 'id',
    `name`              VARCHAR(255) NOT NULL COMMENT '阶段名称',
    `type`              VARCHAR(10)  NOT NULL COMMENT '阶段类型: AFOOT-进行中, END-结束',
    `rate`              VARCHAR(10)  COMMENT '完成率',
    `afoot_roll_back`   BIT(1)       NOT NULL DEFAULT 1 COMMENT '进行中阶段是否可回退',
    `end_roll_back`     BIT(1)       NOT NULL DEFAULT 0 COMMENT '结束阶段是否可回退',
    `pos`               INT           NOT NULL COMMENT '排序',
    `organization_id`   VARCHAR(32)  NOT NULL COMMENT '组织ID',
    `create_time`       BIGINT        NOT NULL COMMENT '创建时间',
    `update_time`       BIGINT        NOT NULL COMMENT '更新时间',
    `create_user`       VARCHAR(32)  NOT NULL COMMENT '创建人',
    `update_user`       VARCHAR(32)  NOT NULL COMMENT '更新人',
    PRIMARY KEY (id)
) COMMENT = '客户阶段配置'
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_general_ci;

CREATE INDEX idx_organization_id ON customer_stage_config (organization_id ASC);

-- 客户表添加 stage 字段
ALTER TABLE customer ADD COLUMN `stage` VARCHAR(32) COMMENT '客户阶段ID' AFTER `reason_id`;

-- 初始化客户阶段配置数据
INSERT INTO `customer_stage_config`(`id`, `name`, `type`, `rate`, `afoot_roll_back`, `end_roll_back`, `pos`, `organization_id`, `create_time`, `update_time`, `create_user`, `update_user`)
VALUES
    ('339600000000000001', '线索', 'AFOOT', '10', b'1', b'0', 1, '100001', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'admin'),
    ('339600000000000002', '初步接触', 'AFOOT', '30', b'1', b'0', 2, '100001', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'admin'),
    ('339600000000000003', '需求了解', 'AFOOT', '50', b'1', b'0', 3, '100001', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'admin'),
    ('339600000000000004', '方案报价', 'AFOOT', '70', b'1', b'0', 4, '100001', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'admin'),
    ('339600000000000005', '商务谈判', 'AFOOT', '90', b'1', b'0', 5, '100001', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'admin'),
    ('339600000000000006', '成交', 'END', '100', b'1', b'0', 6, '100001', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'admin'),
    ('339600000000000007', '流失', 'END', '0', b'1', b'0', 7, '100001', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'admin');

SET SESSION innodb_lock_wait_timeout = DEFAULT;