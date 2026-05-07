ALTER TABLE customer
ADD COLUMN `call_status` tinyint(1) DEFAULT '0' COMMENT '拨打电话状态 0:未拨打 1:拨打未接通 2:拨打已接通' AFTER `mobile`;

ALTER TABLE customer
    ADD COLUMN `wechat_friend_status` tinyint(1) DEFAULT '0' COMMENT '微信好友状态 0:未添加 1:添加未通过 2:已添加' AFTER `call_status`;