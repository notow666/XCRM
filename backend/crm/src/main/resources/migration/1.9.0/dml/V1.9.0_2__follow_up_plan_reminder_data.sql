-- 跟进计划提醒消息事件（按组织初始化）
INSERT INTO sys_message_task (id, event, task_type, email_enable, sys_enable, display, organization_id, template,
                              create_user, create_time, update_user, update_time)
SELECT CAST(UUID_SHORT() AS CHAR),
       'CUSTOMER_FOLLOW_UP_PLAN_REMIND',
       'CUSTOMER',
       0,
       1,
       1,
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
                    AND t.event = 'CUSTOMER_FOLLOW_UP_PLAN_REMIND');

-- 跟进计划表单新增"提醒时间"标准字段（置于预计开始时间之后）
-- 将预计沟通内容、附件后移，为提醒时间腾出位置
UPDATE sys_module_field t1
         INNER JOIN sys_module_form f ON f.id = t1.form_id
SET t1.pos = t1.pos + 1
WHERE f.form_key = 'plan'
  AND t1.internal_key IN ('planContent', 'planAttachment');

INSERT INTO sys_module_field (id, form_id, internal_key, name, type, mobile, deletable, pos,
                              create_user, create_time, update_user, update_time)
SELECT CAST(UUID_SHORT() AS CHAR),
       f.id,
       'planRemindTime',
       '提醒时间',
       'DATE_TIME',
       0,
       0,
       7,
       'admin',
       UNIX_TIMESTAMP() * 1000,
       'admin',
       UNIX_TIMESTAMP() * 1000
FROM sys_module_form f
WHERE f.form_key = 'plan'
  AND NOT EXISTS (SELECT 1
                  FROM sys_module_field mf
                  WHERE mf.form_id = f.id
                    AND mf.internal_key = 'planRemindTime');

INSERT INTO sys_module_field_blob (id, prop)
SELECT mf.id,
       JSON_OBJECT(
               'id', mf.id,
               'name', mf.name,
               'internalKey', mf.internal_key,
               'pos', mf.pos,
               'type', 'DATE_TIME',
               'mobile', FALSE,
               'deletable', FALSE,
               'showLabel', TRUE,
               'readable', TRUE,
               'editable', TRUE,
               'fieldWidth', '0.5',
               'dateType', 'datetime',
               'dateDefaultType', 'custom',
               'rules', JSON_ARRAY()
       )
FROM sys_module_field mf
         INNER JOIN sys_module_form f ON f.id = mf.form_id
WHERE f.form_key = 'plan'
  AND mf.internal_key = 'planRemindTime'
  AND NOT EXISTS (SELECT 1 FROM sys_module_field_blob mfb WHERE mfb.id = mf.id);
