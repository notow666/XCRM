UPDATE schedule
SET value = '0 30 22 * * ?',
    update_time = UNIX_TIMESTAMP() * 1000
WHERE id = 'builtin_customer_auto_delete'
  AND job = 'cn.cordys.crm.customer.job.CustomerAutoDeleteExecuteJob';
