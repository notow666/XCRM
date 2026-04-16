-- set innodb lock wait timeout
SET SESSION innodb_lock_wait_timeout = 7200;

ALTER TABLE sys_module_field
    ADD COLUMN deletable BIT(1) NOT NULL DEFAULT 1 COMMENT '是否可删除' AFTER mobile;

ALTER TABLE clue_pool
    ADD COLUMN distribute BIT(1) NOT NULL DEFAULT 0 COMMENT '自动分发' AFTER auto;

CREATE TABLE `clue_pool_distribute_rule` (
                                          `id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'ID',
                                          `clue_pool_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '线索池ID',
                                          `customer_pool_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '公海池ID',
                                          `operator` varchar(10) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '操作符',
                                          `condition` text COLLATE utf8mb4_general_ci COMMENT '分发条件',
                                          `create_time` bigint(20) NOT NULL COMMENT '创建时间',
                                          `update_time` bigint(20) NOT NULL COMMENT '更新时间',
                                          `create_user` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建人',
                                          `update_user` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '更新人',
                                          PRIMARY KEY (`id`),
                                          KEY `idx_pool_id` (`clue_pool_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='线索池分发规则';

SET SESSION innodb_lock_wait_timeout = DEFAULT;
