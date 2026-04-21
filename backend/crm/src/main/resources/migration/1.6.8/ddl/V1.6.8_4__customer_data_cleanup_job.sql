-- 系统任务定时参数配置表
CREATE TABLE IF NOT EXISTS sys_job_cron_detail (
    id INT PRIMARY KEY AUTO_INCREMENT,
    method_name VARCHAR(100) NOT NULL COMMENT '方法名，如CustomerDataCleanupJob.executeCleanup',
    description VARCHAR(200) COMMENT '方法描述，如客户数据清理任务',
    cron VARCHAR(32) DEFAULT '0 30 12 * * ?' COMMENT 'cron表达式',
    enable BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    create_time DATETIME,
    update_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统任务定时参数配置';

INSERT INTO sys_job_cron_detail (method_name, description, cron,create_time,update_time)
VALUES ('CustomerDataCleanupJob.executeCleanup', '客户数据清理任务', '0 30 12 * * ?',now(),now());
