ALTER TABLE mmba_device
    ADD COLUMN last_online BIGINT DEFAULT NULL COMMENT '最近在线时间戳' AFTER org_names,
    ADD COLUMN last_online_time VARCHAR(32) DEFAULT NULL COMMENT '最近在线时间' AFTER last_online,
    ADD COLUMN login_status INT DEFAULT NULL COMMENT '登录状态' AFTER last_online_time;
