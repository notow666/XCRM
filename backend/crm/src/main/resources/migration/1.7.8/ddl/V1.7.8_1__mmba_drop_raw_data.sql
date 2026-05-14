-- 移除 MMBA 各业务表冗余的 raw_data（LONGTEXT），降低存储；行级原始 JSON 可通过 mmba_callback_record.payload_raw 追溯。

ALTER TABLE mmba_command_result DROP COLUMN raw_data;
ALTER TABLE mmba_device DROP COLUMN raw_data;
ALTER TABLE mmba_device_mapping DROP COLUMN raw_data;
ALTER TABLE mmba_call_record_audit DROP COLUMN raw_data;
ALTER TABLE mmba_sms_record_audit DROP COLUMN raw_data;
ALTER TABLE mmba_wx_account_audit DROP COLUMN raw_data;
ALTER TABLE mmba_wx_chat_audit DROP COLUMN raw_data;
ALTER TABLE mmba_wx_friend_change_audit DROP COLUMN raw_data;
ALTER TABLE mmba_wx_friend_list_audit DROP COLUMN raw_data;
ALTER TABLE mmba_wx_login_audit DROP COLUMN raw_data;
ALTER TABLE mmba_device_info_audit DROP COLUMN raw_data;
ALTER TABLE mmba_media_file DROP COLUMN raw_data;
