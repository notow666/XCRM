-- set innodb lock wait timeout
SET SESSION innodb_lock_wait_timeout = 7200;

CREATE TABLE `number_cube_task` (
    `id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
    `organization_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '组织ID',
    `province` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '省份',
    `city` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '城市',
    `selection_mode` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PARTIAL' COMMENT '选择模式: ALL/PARTIAL',
    `segment_count` int NOT NULL DEFAULT '0' COMMENT '号段数量',
    `status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务状态',
    `error_message` varchar(1024) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '错误信息',
    `plain_pack_file_id` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '明文打包文件ID',
    `masked_pack_file_id` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '脱敏打包文件ID',
    `create_user` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建人',
    `update_user` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '更新人',
    `create_time` bigint NOT NULL COMMENT '创建时间',
    `update_time` bigint NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_number_cube_task_status_time` (`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='号码魔方任务';

CREATE TABLE `number_cube_task_segment` (
    `task_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务ID',
    `segment` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '号段值',
    `prefix` varchar(3) COLLATE utf8mb4_general_ci NOT NULL COMMENT '号段前三位',
    PRIMARY KEY (`task_id`,`segment`),
    KEY `idx_number_cube_task_segment_prefix` (`task_id`,`prefix`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='号码魔方任务号段明细';

ALTER TABLE sys_attachment
    ADD COLUMN module VARCHAR(50) NOT NULL DEFAULT 'system' COMMENT '文件来源模块' AFTER resource_id;

SET SESSION innodb_lock_wait_timeout = DEFAULT;
