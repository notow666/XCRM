ALTER TABLE tenant
    ADD COLUMN `org_id` varchar(32) utf8mb4_general_ci DEFAULT NULL COMMENT 'mmba部门Id' AFTER `status`;
