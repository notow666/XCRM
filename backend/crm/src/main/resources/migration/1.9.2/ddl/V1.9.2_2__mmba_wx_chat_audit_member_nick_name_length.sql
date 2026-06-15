-- 扩容微信群聊审计发言人昵称，兼容供应商把入群提示和群参与人列表写入该字段。

ALTER TABLE mmba_wx_chat_audit
    MODIFY COLUMN member_nick_name LONGTEXT COMMENT '群内发言人昵称';
