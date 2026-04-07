-- set innodb lock wait timeout
SET SESSION innodb_lock_wait_timeout = 7200;

-- 客户表添加 mobile 字段
ALTER TABLE customer ADD COLUMN `mobile` VARCHAR(32) DEFAULT NULL COMMENT '手机号码' AFTER `stage`;

-- 添加手机号在租户内的唯一索引
CREATE UNIQUE INDEX uk_organization_mobile ON customer (organization_id, mobile);

SET SESSION innodb_lock_wait_timeout = DEFAULT;
