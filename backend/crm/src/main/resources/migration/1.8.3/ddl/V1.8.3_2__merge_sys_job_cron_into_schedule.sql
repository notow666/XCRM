-- 将 sys_job_cron_detail 合并到 schedule，并删除旧表

INSERT INTO schedule (id, `key`, type, value, job, resource_type, enable, create_user, create_time, update_time, name, num, organization_id)
SELECT 'builtin_customer_data_cleanup',
       'customer_data_cleanup',
       'cron',
       COALESCE(d.cron, '0 30 12 * * ?'),
       'cn.cordys.crm.customer.job.CustomerDataCleanupExecuteJob',
       'BUILTIN_CUSTOMER_DATA_CLEANUP',
       IF(IFNULL(d.enable, 1), 1, 0),
       'system',
       COALESCE(UNIX_TIMESTAMP(d.create_time), UNIX_TIMESTAMP()) * 1000,
       COALESCE(UNIX_TIMESTAMP(d.update_time), UNIX_TIMESTAMP()) * 1000,
       COALESCE(d.description, '客户数据清理任务'),
       1, '100001'
FROM sys_job_cron_detail d
WHERE d.method_name = 'CustomerDataCleanupJob.executeCleanup'
  AND NOT EXISTS (SELECT 1 FROM schedule s WHERE s.id = 'builtin_customer_data_cleanup');

INSERT INTO schedule (id, `key`, type, value, job, resource_type, enable, create_user, create_time, update_time, name, num, organization_id)
SELECT 'builtin_customer_data_cleanup',
       'customer_data_cleanup',
       'cron',
       '0 30 12 * * ?',
       'cn.cordys.crm.customer.job.CustomerDataCleanupExecuteJob',
       'BUILTIN_CUSTOMER_DATA_CLEANUP',
       1,
       'system',
       UNIX_TIMESTAMP() * 1000,
       UNIX_TIMESTAMP() * 1000,
       '客户数据清理任务',
       1, '100001'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM schedule s WHERE s.id = 'builtin_customer_data_cleanup');

DROP TABLE IF EXISTS sys_job_cron_detail;
