-- 客户数据清理配置表
CREATE TABLE IF NOT EXISTS customer_data_cleanup_config (
    id VARCHAR(32) NOT NULL COMMENT '主键ID',
    organization_id VARCHAR(32) NOT NULL COMMENT '租户ID',
    field_ids JSON COMMENT '需清理的字段ID列表',
    days INT NOT NULL COMMENT '多少天后清理',
    create_time BIGINT COMMENT '创建时间',
    create_user VARCHAR(32) COMMENT '创建人',
    update_time BIGINT COMMENT '更新时间',
    update_user VARCHAR(32) COMMENT '更新人',
    PRIMARY KEY (id),
    KEY idx_organization_id (organization_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户数据清理配置';