SET SESSION innodb_lock_wait_timeout = 7200;

ALTER TABLE mmba_device_info_audit
    DROP INDEX uk_mmba_device_info_audit;

ALTER TABLE mmba_device_info_audit
    ADD UNIQUE KEY uk_mmba_device_info_audit (device_id, timestamp);

SET SESSION innodb_lock_wait_timeout = DEFAULT;
