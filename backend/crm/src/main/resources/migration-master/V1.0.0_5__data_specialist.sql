CREATE TABLE IF NOT EXISTS data_specialist (
    id                VARCHAR(64)  NOT NULL PRIMARY KEY COMMENT '主键',
    username          VARCHAR(64)  NOT NULL COMMENT '登录名',
    password_hash     VARCHAR(128) NOT NULL COMMENT '密码摘要',
    enabled           BIT(1)       NOT NULL DEFAULT b'1' COMMENT '是否启用',
    create_time       BIGINT       NOT NULL COMMENT '创建时间',
    update_time       BIGINT       NOT NULL COMMENT '更新时间',
    create_user       VARCHAR(64)  NOT NULL COMMENT '创建人',
    update_user       VARCHAR(64)  NOT NULL COMMENT '更新人',
    UNIQUE KEY uk_data_specialist_username (username)
) COMMENT = '数据专员（master）';

CREATE TABLE IF NOT EXISTS data_specialist_tenant (
    specialist_id VARCHAR(64) NOT NULL COMMENT '数据专员ID',
    tenant_id     VARCHAR(64) NOT NULL COMMENT '可导入租户ID',
    PRIMARY KEY (specialist_id, tenant_id),
    KEY idx_dst_tenant (tenant_id)
) COMMENT = '数据专员可导入租户';
