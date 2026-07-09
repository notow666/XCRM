ALTER TABLE tenant
    ADD COLUMN shadow_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否开通影子库' AFTER org_id,
    ADD COLUMN active_db_role VARCHAR(16) NOT NULL DEFAULT 'PRIMARY' COMMENT '当前 UI 指向库：PRIMARY|SHADOW' AFTER shadow_enabled,
    ADD COLUMN shadow_maintenance_state VARCHAR(32) NULL DEFAULT NULL COMMENT '维护状态：PRE_NOTICE|BLOCKING' AFTER active_db_role,
    ADD COLUMN shadow_maintenance_until BIGINT NULL DEFAULT NULL COMMENT '维护截止时间戳(ms)' AFTER shadow_maintenance_state;
