ALTER TABLE customer
    ADD INDEX idx_customer_mobile_owner_pool (mobile, owner, in_shared_pool);

ALTER TABLE mmba_call_record_audit
    ADD INDEX idx_mmba_call_direction_begin_ts_tel_um (direction, begin_timestamp, customer_tel, um);
