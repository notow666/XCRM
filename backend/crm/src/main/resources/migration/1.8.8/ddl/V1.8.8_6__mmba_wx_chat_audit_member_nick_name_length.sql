-- 扩容微信群聊审计发言人昵称，避免供应商回调昵称超过 128 字符时写入失败。

ALTER TABLE mmba_wx_chat_audit
    MODIFY COLUMN member_nick_name VARCHAR(512) DEFAULT NULL COMMENT '群内发言人昵称';
