-- 租户业务库黑名单；通过现有 classpath:migration 加载。
CREATE TABLE customer_blacklist (
    id VARCHAR(50) NOT NULL,
    mobile VARCHAR(11) NOT NULL,
    customer_name VARCHAR(255) NULL,
    create_time BIGINT NOT NULL,
    create_user VARCHAR(50) NOT NULL,
    update_time BIGINT NOT NULL,
    update_user VARCHAR(50) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_blacklist_mobile (mobile),
    KEY idx_blacklist_update (update_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户手机黑名单';
