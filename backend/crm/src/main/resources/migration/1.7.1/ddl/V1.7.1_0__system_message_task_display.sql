ALTER TABLE sys_message_task
ADD COLUMN `display` bit(1) DEFAULT b'1' COMMENT '隐藏、显示' AFTER `lark_enable`;