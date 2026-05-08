ALTER TABLE mmba_command_result
    ADD COLUMN process_msg VARCHAR(1024) DEFAULT NULL COMMENT '处理详情' AFTER operate_time;

ALTER TABLE mmba_call_record_audit
    ADD COLUMN retry INT DEFAULT NULL COMMENT '重试次数' AFTER req_id;
