UPDATE schedule
SET value = '0 30 1 * * ?',
    update_time = UNIX_TIMESTAMP() * 1000
WHERE id = 'builtin_customer_data_cleanup';

INSERT INTO schedule (id, `key`, type, value, job, resource_type, enable, create_user, create_time, update_time, name, num, organization_id)
SELECT 'builtin_customer_auto_delete',
       'customer_auto_delete',
       'cron',
       '0 30 0 * * ?',
       'cn.cordys.crm.customer.job.CustomerAutoDeleteExecuteJob',
       'BUILTIN_CUSTOMER_AUTO_DELETE',
       1,
       'system',
       UNIX_TIMESTAMP() * 1000,
       UNIX_TIMESTAMP() * 1000,
       '客户定时删除任务',
       1,
       '100001'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM schedule s WHERE s.id = 'builtin_customer_auto_delete');
