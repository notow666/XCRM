-- set innodb lock wait timeout
SET SESSION innodb_lock_wait_timeout = 7200;

DROP INDEX uk_mmba_device_device_id ON mmba_device;

SET SESSION innodb_lock_wait_timeout = DEFAULT;
