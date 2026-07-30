CREATE TABLE IF NOT EXISTS customer_private_auto_delete_config (
    id VARCHAR(32) NOT NULL COMMENT '主键ID',
    organization_id VARCHAR(32) NOT NULL COMMENT '组织ID',
    days INT NOT NULL COMMENT '连续多少个自然日未跟进和更新后删除',
    create_time BIGINT COMMENT '创建时间',
    create_user VARCHAR(32) COMMENT '创建人',
    update_time BIGINT COMMENT '更新时间',
    update_user VARCHAR(32) COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_customer_private_auto_delete_org (organization_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='私海客户定时删除配置';

CREATE INDEX idx_customer_private_auto_delete
    ON customer (organization_id, in_shared_pool, create_source, id);

CREATE TABLE IF NOT EXISTS customer_contract_delete_policy (
    id VARCHAR(32) NOT NULL COMMENT '主键ID',
    organization_id VARCHAR(32) NOT NULL COMMENT '组织ID',
    policy VARCHAR(32) NOT NULL COMMENT '合同删除策略：CASCADE、KEEP_CONTRACT',
    create_time BIGINT COMMENT '创建时间',
    create_user VARCHAR(32) COMMENT '创建人',
    update_time BIGINT COMMENT '更新时间',
    update_user VARCHAR(32) COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_customer_contract_delete_policy_org (organization_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户合同删除策略配置';

INSERT INTO customer_contract_delete_policy (
    id,
    organization_id,
    policy,
    create_time,
    create_user,
    update_time,
    update_user
) VALUES (
    UUID_SHORT(),
    '100001',
    'CASCADE',
    UNIX_TIMESTAMP() * 1000,
    'admin',
    UNIX_TIMESTAMP() * 1000,
    'admin'
);
