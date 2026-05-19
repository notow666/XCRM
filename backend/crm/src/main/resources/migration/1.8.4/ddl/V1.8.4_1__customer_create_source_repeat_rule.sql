ALTER TABLE customer
    ADD COLUMN create_source VARCHAR(32) DEFAULT NULL COMMENT '创建来源: MANUAL_CREATE/PRIVATE_IMPORT/POOL_IMPORT' AFTER mobile;

ALTER TABLE customer
    DROP INDEX uk_organization_mobile;

CREATE INDEX idx_customer_mobile ON customer (mobile);
CREATE INDEX idx_customer_create_source_mobile ON customer (create_source, mobile);
CREATE INDEX idx_customer_pool_mobile_shared ON customer (pool_id, mobile, in_shared_pool);

CREATE TABLE IF NOT EXISTS customer_repeat_rule_config (
    id            VARCHAR(32)  NOT NULL COMMENT '主键',
    enabled       BIT(1)       NOT NULL DEFAULT b'0' COMMENT '是否启用重复规则',
    repeat_after_days INT      NOT NULL DEFAULT 5 COMMENT '允许重复前的天数阈值',
    create_time   BIGINT       NOT NULL COMMENT '创建时间',
    update_time   BIGINT       NOT NULL COMMENT '更新时间',
    create_user   VARCHAR(32)  NOT NULL COMMENT '创建人',
    update_user   VARCHAR(32)  NOT NULL COMMENT '更新人',
    PRIMARY KEY (id)
) COMMENT='客户重复规则配置';
