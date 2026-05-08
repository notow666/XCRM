-- 允许设备序列号为空，与「新增」表单非必填一致
SET SESSION innodb_lock_wait_timeout = 7200;

ALTER TABLE mmba_device
    MODIFY COLUMN device_id VARCHAR(64) NULL COMMENT '设备ID';

SET SESSION innodb_lock_wait_timeout = DEFAULT;
