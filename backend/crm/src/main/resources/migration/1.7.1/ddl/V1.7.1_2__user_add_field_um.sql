ALTER TABLE sys_user
ADD COLUMN `um` varchar(16) DEFAULT null COMMENT 'mmba用户唯一标识' AFTER `password`;