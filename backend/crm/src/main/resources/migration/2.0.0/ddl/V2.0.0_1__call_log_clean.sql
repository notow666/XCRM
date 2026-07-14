CREATE TABLE IF NOT EXISTS mmba_call_log_clean_config (
    id VARCHAR(32) NOT NULL COMMENT '主键ID',
    organization_id VARCHAR(32) NOT NULL COMMENT '组织ID',
    enable TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否开启',
    create_time BIGINT COMMENT '创建时间',
    create_user VARCHAR(32) COMMENT '创建人',
    update_time BIGINT COMMENT '更新时间',
    update_user VARCHAR(32) COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_call_log_clean_org (organization_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时清除通话记录配置';

CREATE TABLE IF NOT EXISTS mmba_call_log_clean_config_user (
    id VARCHAR(32) NOT NULL COMMENT '主键ID',
    config_id VARCHAR(32) NOT NULL COMMENT '配置ID',
    user_id VARCHAR(32) NOT NULL COMMENT '员工用户ID',
    create_time BIGINT COMMENT '创建时间',
    create_user VARCHAR(32) COMMENT '创建人',
    update_time BIGINT COMMENT '更新时间',
    update_user VARCHAR(32) COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_call_log_clean_config_user (config_id, user_id),
    KEY idx_call_log_clean_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时清除通话记录员工关系';

INSERT INTO schedule (id, `key`, type, value, job, resource_type, enable, create_user, create_time, update_time, name, num, organization_id)
SELECT 'builtin_call_log_clean',
       'call_log_clean',
       'cron',
       '0 0 1 * * ?',
       'cn.cordys.mmba.job.MmbaCallLogCleanExecuteJob',
       'BUILTIN_CALL_LOG_CLEAN',
       1,
       'system',
       UNIX_TIMESTAMP() * 1000,
       UNIX_TIMESTAMP() * 1000,
       '定时清除通话记录任务',
       1,
       '100001'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM schedule s WHERE s.id = 'builtin_call_log_clean');
