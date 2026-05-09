-- 删除um字段，id = um
SET SESSION innodb_lock_wait_timeout = 7200;

DROP INDEX idx_mmba_device_um ON mmba_device;

ALTER TABLE mmba_device
    DROP COLUMN um;

ALTER TABLE mmba_device
    MODIFY COLUMN id varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键ID=um';

ALTER TABLE mmba_device
    ADD COLUMN `enable` bit(1) NOT NULL DEFAULT b'1' COMMENT '用户启用/禁用';

SET SESSION innodb_lock_wait_timeout = DEFAULT;
