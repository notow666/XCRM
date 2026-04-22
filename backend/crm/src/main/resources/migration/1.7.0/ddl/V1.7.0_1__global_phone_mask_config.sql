CREATE TABLE IF NOT EXISTS global_phone_mask_config (
    id VARCHAR(32) NOT NULL COMMENT '主键ID',
    organization_id VARCHAR(32) NOT NULL COMMENT '组织ID',
    enabled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否开启全局手机号脱敏',
    create_time BIGINT COMMENT '创建时间',
    create_user VARCHAR(32) COMMENT '创建人',
    update_time BIGINT COMMENT '更新时间',
    update_user VARCHAR(32) COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_organization_id (organization_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='全局手机号脱敏配置';
