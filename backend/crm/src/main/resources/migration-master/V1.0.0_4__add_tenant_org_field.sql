ALTER TABLE tenant
    ADD COLUMN `org_id` varchar(32) DEFAULT NULL COMMENT 'mmba部门Id' AFTER `status`;
