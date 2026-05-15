-- 公海客户导入完成站内通知（按组织初始化消息任务）
INSERT INTO sys_message_task (id, event, task_type, email_enable, sys_enable, display, organization_id, template,
                              create_user, create_time, update_user, update_time)
SELECT CAST(UUID_SHORT() AS CHAR),
       'CUSTOMER_IMPORT',
       'CUSTOMER',
       0,
       1,
       0,
       o.id,
       NULL,
       'admin',
       UNIX_TIMESTAMP() * 1000,
       'admin',
       UNIX_TIMESTAMP() * 1000
FROM sys_organization o
WHERE NOT EXISTS (SELECT 1
                  FROM sys_message_task t
                  WHERE t.organization_id = o.id
                    AND t.task_type = 'CUSTOMER'
                    AND t.event = 'CUSTOMER_IMPORT');
